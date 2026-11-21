package com.example.chatmultistream.data.client

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.example.chatmultistream.model.ChatEvent
import com.example.chatmultistream.model.ChatEventType
import com.example.chatmultistream.model.ChatMessage
import com.example.chatmultistream.model.ConnectionState
import com.example.chatmultistream.model.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class YouTubeChatClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
) : ChatClient {

    override val platform: Platform = Platform.YOUTUBE

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messageFlow = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 128)
    override val messageFlow: SharedFlow<ChatMessage> = _messageFlow.asSharedFlow()

    private var pollingJob: Job? = null

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    override suspend fun connect(channelName: String, authToken: String?) {
        val cleanInput = channelName.trim()
        if (cleanInput.isEmpty()) {
            _connectionState.value = ConnectionState.Error("Ingresa un Video ID o Canal de YouTube válido")
            return
        }

        disconnect()
        _connectionState.value = ConnectionState.Connecting

        val videoId = resolveVideoId(cleanInput)
        if (videoId == null) {
            _connectionState.value = ConnectionState.Error("No se pudo identificar un directo activo para: $cleanInput")
            return
        }

        pollingJob = scope.launch {
            runLiveChatReader(videoId)
        }
    }

    override suspend fun disconnect() {
        pollingJob?.cancel()
        pollingJob = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun close() {
        pollingJob?.cancel()
        pollingJob = null
        _connectionState.value = ConnectionState.Disconnected
        scope.cancel()
    }

    private suspend fun resolveVideoId(input: String): String? = withContext(Dispatchers.IO) {
        // 1. Direct 11-char YouTube Video ID (e.g. dQw4w9WgXcQ)
        if (input.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return@withContext input
        }

        // 2. Full URL (watch?v=..., youtu.be/..., youtube.com/live/...)
        val vParamMatcher = Pattern.compile("""[?&]v=([a-zA-Z0-9_-]{11})""").matcher(input)
        if (vParamMatcher.find()) {
            return@withContext vParamMatcher.group(1)
        }
        val liveUrlMatcher = Pattern.compile("""youtube\.com/live/([a-zA-Z0-9_-]{11})""").matcher(input)
        if (liveUrlMatcher.find()) {
            return@withContext liveUrlMatcher.group(1)
        }
        val youtuBeMatcher = Pattern.compile("""youtu\.be/([a-zA-Z0-9_-]{11})""").matcher(input)
        if (youtuBeMatcher.find()) {
            return@withContext youtuBeMatcher.group(1)
        }

        // 3. Channel handle (e.g. @streamer or streamer) -> fetch https://www.youtube.com/@handle/live
        val handle = if (input.startsWith("@")) input else "@$input"
        try {
            val liveUrl = "https://www.youtube.com/$handle/live"
            val request = Request.Builder()
                .url(liveUrl)
                .header("User-Agent", userAgent)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val finalUrl = response.request.url.toString()
                    val matcher = Pattern.compile("""[?&]v=([a-zA-Z0-9_-]{11})""").matcher(finalUrl)
                    if (matcher.find()) {
                        return@withContext matcher.group(1)
                    }
                    val altMatcher = Pattern.compile("""/live/([a-zA-Z0-9_-]{11})""").matcher(finalUrl)
                    if (altMatcher.find()) {
                        return@withContext altMatcher.group(1)
                    }

                    // Look into HTML for canonical videoId
                    val html = response.body?.string() ?: ""
                    val canonicalMatcher = Pattern.compile(""""videoId":"([a-zA-Z0-9_-]{11})"""").matcher(html)
                    if (canonicalMatcher.find()) {
                        return@withContext canonicalMatcher.group(1)
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }

    private data class LiveSession(
        val apiKey: String,
        val clientVersion: String,
        val continuationToken: String
    )

    /** Reabre la sesión (clave Innertube + continuation) con backoff si la red falla. */
    private suspend fun runLiveChatReader(videoId: String) {
        var sessionFailures = 0

        while (currentCoroutineContext().isActive) {
            val session = try {
                createSession(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionFailures++
                Log.w(TAG, "No se pudo abrir la sesión de YouTube (intento $sessionFailures): ${e.message}")
                if (sessionFailures == ERROR_THRESHOLD) {
                    _connectionState.value = ConnectionState.Error(
                        e.localizedMessage ?: "Sin conexión con YouTube"
                    )
                }
                delay(backoffMs(sessionFailures))
                continue
            }

            if (session == null) {
                _connectionState.value = ConnectionState.Error("El video de YouTube no tiene un chat en vivo activo")
                return
            }

            sessionFailures = 0
            _connectionState.value = ConnectionState.Connected(videoId)

            val chatEnded = pollSession(session)
            if (chatEnded) {
                _connectionState.value = ConnectionState.Error("El chat en vivo de YouTube terminó o ya no está disponible.")
                return
            }

            sessionFailures++
            _connectionState.value = ConnectionState.Connecting
            delay(backoffMs(sessionFailures))
        }
    }

    /**
     * Descarga la página del chat y extrae la clave Innertube y el token inicial.
     * Devuelve `null` cuando el video no tiene chat en vivo (caso definitivo).
     */
    private suspend fun createSession(videoId: String): LiveSession? {
        val request = Request.Builder()
            .url("https://www.youtube.com/live_chat?v=$videoId")
            .header("User-Agent", userAgent)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("YouTube respondió HTTP ${response.code}")
            }
            val html = response.body?.string().orEmpty()

            val keyMatcher = Pattern.compile(""""INNERTUBE_API_KEY":"([^"]+)"""").matcher(html)
            val apiKey = if (keyMatcher.find()) keyMatcher.group(1) else null

            val versionMatcher = Pattern.compile(""""clientVersion":"([^"]+)"""").matcher(html)
            val clientVersion = if (versionMatcher.find()) versionMatcher.group(1) else null

            val continuationMatcher = Pattern.compile(""""continuation":"([^"]+)"""").matcher(html)
            val continuationToken = if (continuationMatcher.find()) continuationMatcher.group(1) else null

            if (apiKey.isNullOrEmpty() || continuationToken.isNullOrEmpty()) return null

            return LiveSession(
                apiKey = apiKey,
                clientVersion = clientVersion ?: DEFAULT_CLIENT_VERSION,
                continuationToken = continuationToken
            )
        }
    }

    /**
     * @return `true` si el chat terminó de forma definitiva; `false` si hay que
     *         reconstruir la sesión (token caducado o fallos de red repetidos).
     */
    private suspend fun pollSession(session: LiveSession): Boolean {
        var continuationToken: String? = session.continuationToken
        var consecutiveErrors = 0
        var consecutiveEmptyPolls = 0

        while (currentCoroutineContext().isActive) {
            if (continuationToken == null) return true

            // Cadencia fija: se mide el tiempo del ciclo completo para descontar
            // la red, en lugar de sumarle el intervalo después de cada respuesta.
            val startedAt = System.nanoTime()
            var nextIntervalMs = ACTIVE_POLL_INTERVAL_MS
            var messagesSeen = 0

            try {
                val apiUrl = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=${session.apiKey}"
                val jsonPayload = JSONObject().apply {
                    put("continuation", continuationToken)
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "WEB")
                            put("clientVersion", session.clientVersion)
                        })
                    })
                }

                val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                val postRequest = Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .header("User-Agent", userAgent)
                    .build()

                okHttpClient.newCall(postRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("YouTube respondió HTTP ${response.code}")
                    }

                    val rawJson = response.body?.string().orEmpty()
                    val root = JSONObject(rawJson)

                    val continuationContents = root.optJSONObject("continuationContents")
                        ?.optJSONObject("liveChatContinuation")
                        ?: return false // token caducado o chat cerrado: reconstruir sesión

                    messagesSeen = emitActions(continuationContents)
                    continuationToken = extractNextContinuation(continuationContents)
                }

                consecutiveErrors = 0
                if (continuationToken == null) return true

                if (messagesSeen > 0) {
                    consecutiveEmptyPolls = 0
                    nextIntervalMs = ACTIVE_POLL_INTERVAL_MS
                } else {
                    consecutiveEmptyPolls++
                    nextIntervalMs = if (consecutiveEmptyPolls >= IDLE_AFTER_EMPTY_POLLS) {
                        IDLE_POLL_INTERVAL_MS
                    } else {
                        ACTIVE_POLL_INTERVAL_MS
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveErrors++
                Log.w(TAG, "Fallo leyendo el chat de YouTube (intento $consecutiveErrors): ${e.message}")
                if (consecutiveErrors == ERROR_THRESHOLD) {
                    _connectionState.value = ConnectionState.Error(
                        e.localizedMessage ?: "Sin conexión con YouTube"
                    )
                }
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) return false
                nextIntervalMs = backoffMs(consecutiveErrors)
            }

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            delay((nextIntervalMs - elapsedMs).coerceAtLeast(MIN_POLL_DELAY_MS))
        }
        return false
    }

    /** Devuelve el siguiente token soportando `timed` e `invalidation`. */
    private fun extractNextContinuation(continuationContents: JSONObject): String? {
        val continuations = continuationContents.optJSONArray("continuations") ?: return null
        for (i in 0 until continuations.length()) {
            val entry = continuations.optJSONObject(i) ?: continue
            val data = entry.optJSONObject("timedContinuationData")
                ?: entry.optJSONObject("invalidationContinuationData")
                ?: continue
            val token = data.optString("continuation", "").takeIf { it.isNotBlank() }
            if (token != null) return token
        }
        return null
    }

    private suspend fun emitActions(continuationContents: JSONObject): Int {
        var emitted = 0
        val actions = continuationContents.optJSONArray("actions") ?: return 0
        for (i in 0 until actions.length()) {
            val action = actions.optJSONObject(i) ?: continue
            val item = action.optJSONObject("addChatItemAction")?.optJSONObject("item") ?: continue
            val message = parseChatItem(item) ?: continue
            _messageFlow.emit(message)
            emitted++
        }
        return emitted
    }

    /**
     * Convierte un `item` de `addChatItemAction` en un [ChatMessage].
     *
     * Cubre mensajes normales, Super Chat, Super Sticker, membresías y regalos
     * de membresías. Las donaciones y membresías se marcan con [ChatEvent] para
     * que la interfaz las destaque como tarjetas de suceso.
     */
    internal fun parseChatItem(item: JSONObject): ChatMessage? {
        return when {
            item.has("liveChatTextMessageRenderer") -> {
                val renderer = item.optJSONObject("liveChatTextMessageRenderer") ?: return null
                ChatMessage(
                    platform = Platform.YOUTUBE,
                    username = authorName(renderer) ?: "YTViewer",
                    message = runsToText(renderer.optJSONObject("message")),
                    timestamp = System.currentTimeMillis(),
                    userColor = Color(0xFFFF0000)
                )
            }

            item.has("liveChatPaidMessageRenderer") -> {
                val renderer = item.optJSONObject("liveChatPaidMessageRenderer") ?: return null
                ChatMessage(
                    platform = Platform.YOUTUBE,
                    username = authorName(renderer) ?: "SuperChat",
                    message = runsToText(renderer.optJSONObject("message")),
                    timestamp = System.currentTimeMillis(),
                    userColor = Color(0xFFFFD700),
                    event = ChatEvent(
                        type = ChatEventType.DONATION,
                        amountText = renderer.optJSONObject("purchaseAmountText")
                            ?.optString("simpleText")
                            ?.takeIf { it.isNotBlank() }
                    )
                )
            }

            item.has("liveChatPaidStickerRenderer") -> {
                val renderer = item.optJSONObject("liveChatPaidStickerRenderer") ?: return null
                ChatMessage(
                    platform = Platform.YOUTUBE,
                    username = authorName(renderer) ?: "SuperSticker",
                    message = "",
                    timestamp = System.currentTimeMillis(),
                    userColor = Color(0xFFFFD700),
                    event = ChatEvent(
                        type = ChatEventType.SUPER_STICKER,
                        amountText = renderer.optJSONObject("purchaseAmountText")
                            ?.optString("simpleText")
                            ?.takeIf { it.isNotBlank() }
                    )
                )
            }

            item.has("liveChatMembershipItemRenderer") -> {
                val renderer = item.optJSONObject("liveChatMembershipItemRenderer") ?: return null
                ChatMessage(
                    platform = Platform.YOUTUBE,
                    username = authorName(renderer) ?: "YTViewer",
                    // El mensaje de hito ("Member for 6 months") es opcional;
                    // la etiqueta del suceso ya indica que es una membresía.
                    message = runsToText(renderer.optJSONObject("message")),
                    timestamp = System.currentTimeMillis(),
                    userColor = Color(0xFFFF0000),
                    event = ChatEvent(ChatEventType.MEMBERSHIP)
                )
            }

            item.has("liveChatSponsorshipsGiftPurchaseAnnouncementRenderer") -> {
                val announcement = item.optJSONObject(
                    "liveChatSponsorshipsGiftPurchaseAnnouncementRenderer"
                ) ?: return null
                val header = announcement.optJSONObject("header")
                    ?.optJSONObject("liveChatSponsorshipsHeaderRenderer")
                val author = header?.let { authorName(it) }
                    ?: authorName(announcement)
                    ?: "YTViewer"
                val count = giftCount(header?.optJSONObject("primaryText"))
                ChatMessage(
                    platform = Platform.YOUTUBE,
                    username = author,
                    message = "",
                    timestamp = System.currentTimeMillis(),
                    userColor = Color(0xFFFF0000),
                    event = ChatEvent(
                        type = ChatEventType.GIFT_MEMBERSHIP,
                        count = count
                    )
                )
            }

            item.has("liveChatSponsorshipsGiftRedemptionAnnouncementRenderer") -> {
                val announcement = item.optJSONObject(
                    "liveChatSponsorshipsGiftRedemptionAnnouncementRenderer"
                ) ?: return null
                ChatMessage(
                    platform = Platform.YOUTUBE,
                    username = authorName(announcement) ?: "YTViewer",
                    message = runsToText(announcement.optJSONObject("message")),
                    timestamp = System.currentTimeMillis(),
                    userColor = Color(0xFFFF0000),
                    event = ChatEvent(ChatEventType.GIFT_MEMBERSHIP, count = 1)
                )
            }

            else -> null
        }
    }

    private fun authorName(renderer: JSONObject): String? =
        renderer.optJSONObject("authorName")?.optString("simpleText")
            ?.takeIf { it.isNotBlank() }

    /** Une los `runs` de un texto de YouTube (texto plano + emojis). */
    internal fun runsToText(message: JSONObject?): String {
        val runs = message?.optJSONArray("runs") ?: return ""
        val text = StringBuilder()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            when {
                run.has("text") -> text.append(run.optString("text"))
                run.has("emoji") -> {
                    val shortcut = run.optJSONObject("emoji")
                        ?.optJSONArray("shortcuts")
                        ?.optString(0)
                    text.append(shortcut ?: "✨")
                }
            }
        }
        return text.toString()
    }

    /**
     * El texto del aviso de regalo está localizado por YouTube
     * ("Gifted 5 memberships"). Solo se extrae el número; la frase que se
     * muestra la compone la app en su idioma.
     */
    private fun giftCount(primaryText: JSONObject?): Int =
        GIFT_COUNT_REGEX.find(runsToText(primaryText))?.value?.toIntOrNull() ?: 1

    private fun backoffMs(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 5)
        return (1_000L shl exponent).coerceAtMost(MAX_BACKOFF_MS)
    }

    companion object {
        private const val TAG = "YouTubeChatClient"
        private const val DEFAULT_CLIENT_VERSION = "2.20240101.00.00"

        /**
         * Cadencia con actividad. Antes se usaba el `timeoutMs` del servidor
         * (≈15 s en chats tranquilos) y además se sumaba un `delay` después de
         * cada respuesta: los mensajes llegaban en ráfagas cada ~8 s.
         */
        private const val ACTIVE_POLL_INTERVAL_MS = 1_000L

        /** Cadencia cuando el chat lleva varios sondeos sin mensajes. */
        private const val IDLE_POLL_INTERVAL_MS = 2_500L
        private const val IDLE_AFTER_EMPTY_POLLS = 6
        private const val MIN_POLL_DELAY_MS = 250L
        private const val ERROR_THRESHOLD = 3
        private const val MAX_CONSECUTIVE_ERRORS = 5
        private const val MAX_BACKOFF_MS = 30_000L
        private val GIFT_COUNT_REGEX = Regex("\\d+")
    }
}
