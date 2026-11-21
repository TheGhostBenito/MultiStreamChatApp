package com.example.chatmultistream.data.client

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.example.chatmultistream.model.ChatEvent
import com.example.chatmultistream.model.ChatEventType
import com.example.chatmultistream.model.ChatMessage
import com.example.chatmultistream.model.ConnectionState
import com.example.chatmultistream.model.EmoteSpan
import com.example.chatmultistream.model.Platform
import kotlinx.coroutines.CancellableContinuation
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Cliente de chat de Kick.
 *
 * Usa el realtime que emplea la propia web: se pide una conexión al backend
 * (`/v1/realtime/...`), se obtiene un token de invitado y se abre un WebSocket
 * Centrifugo. Los mensajes llegan en el momento en que se publican, sin el
 * retardo de 1,5 s (más el tiempo de red) que introducía el polling.
 *
 * El polling del historial (`/api/v2/channels/{id}/messages`) se conserva como
 * respaldo: si el realtime no está disponible (por ejemplo al conectar con un
 * id numérico, del que no se puede resolver el chatroom) se sigue usando.
 */
class KickChatClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val webSocketClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
) : ChatClient {

    override val platform: Platform = Platform.KICK

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messageFlow = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 256)
    override val messageFlow: SharedFlow<ChatMessage> = _messageFlow.asSharedFlow()

    private var connectionJob: Job? = null

    /** Identidad anónima que Kick asocia a esta instalación. */
    private val clientId: String = UUID.randomUUID().toString()

    /** Ids ya emitidos, para no duplicar entre realtime, reconexiones y polling. */
    private val seenMessageIds = LinkedHashSet<String>()

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private data class ResolvedChannel(
        val channelId: String,
        /** Necesario para suscribirse al canal `chatrooms.{id}.v2` del realtime. */
        val chatroomId: String?,
        val displayName: String,
        val isLive: Boolean?
    )

    override suspend fun connect(channelName: String, authToken: String?) {
        val cleanInput = sanitizeInput(channelName)
        if (cleanInput.isEmpty()) {
            _connectionState.value = ConnectionState.Error("Nombre de canal inválido")
            return
        }

        disconnect()
        _connectionState.value = ConnectionState.Connecting

        val resolved = try {
            resolveChannel(cleanInput)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo resolver el canal '$cleanInput': ${e.message}")
            _connectionState.value = ConnectionState.Error(
                "No se pudo conectar con Kick. Revisa tu conexión e inténtalo de nuevo."
            )
            return
        }

        if (resolved == null) {
            _connectionState.value = ConnectionState.Error(
                "No se encontró el canal '$cleanInput' en Kick."
            )
            return
        }

        Log.d(
            TAG,
            "Canal resuelto: $cleanInput -> id=${resolved.channelId} " +
                "chatroom=${resolved.chatroomId} (live=${resolved.isLive})"
        )
        _connectionState.value = ConnectionState.Connected(resolved.displayName)

        connectionJob = scope.launch {
            runChatLoop(resolved)
        }
    }

    override suspend fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun close() {
        connectionJob?.cancel()
        connectionJob = null
        _connectionState.value = ConnectionState.Disconnected
        scope.cancel()
    }

    internal fun sanitizeInput(raw: String): String =
        raw.trim()
            .removePrefix("#")
            .removePrefix("https://kick.com/")
            .removePrefix("http://kick.com/")
            .trimEnd('/')
            .substringAfterLast('/')
            .lowercase(Locale.US)

    /**
     * Acepta un slug (`westcol`) o un channel id numérico (`875396`).
     * El endpoint de mensajes espera el channel id, NO el chatroom id.
     */
    private suspend fun resolveChannel(input: String): ResolvedChannel? = withContext(Dispatchers.IO) {
        if (input.isNotEmpty() && input.all { it.isDigit() }) {
            // Con un id numérico Kick no expone el chatroom id, así que no se
            // puede montar el realtime. El polling sigue funcionando igual.
            return@withContext ResolvedChannel(
                channelId = input,
                chatroomId = null,
                displayName = "ID $input",
                isLive = null
            )
        }

        val request = Request.Builder()
            .url("https://kick.com/api/v2/channels/$input")
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://kick.com/$input")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Kick API respondió ${response.code} para '$input'")
                return@withContext null
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@withContext null

            val root = JSONObject(body)
            val channelId = root.optString("id").takeIf { it.isNotBlank() && it != "0" }
                ?: return@withContext null
            val slug = root.optString("slug").takeIf { it.isNotBlank() } ?: input
            val isLive = root.optJSONObject("livestream")?.optBoolean("is_live", false) ?: false
            val chatroomId = root.optJSONObject("chatroom")
                ?.optString("id")
                ?.takeIf { it.isNotBlank() && it != "0" }

            val displayName = if (isLive) "$slug (en vivo)" else "$slug (sin directo)"
            ResolvedChannel(
                channelId = channelId,
                chatroomId = chatroomId,
                displayName = displayName,
                isLive = isLive
            )
        }
    }

    private suspend fun runChatLoop(channel: ResolvedChannel) {
        if (!channel.chatroomId.isNullOrBlank()) {
            var failures = 0
            var everSubscribed = false

            while (currentCoroutineContext().isActive) {
                var subscribedThisSession = false
                try {
                    openRealtimeSession(channel) {
                        subscribedThisSession = true
                        everSubscribed = true
                        _connectionState.value = ConnectionState.Connected(channel.displayName)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime de Kick no disponible (intento ${failures + 1}): ${e.message}")
                }

                if (subscribedThisSession) {
                    failures = 0
                } else {
                    failures++
                    if (!everSubscribed && failures >= MAX_REALTIME_ATTEMPTS) {
                        Log.w(TAG, "El realtime no respondió; se usa polling de respaldo")
                        break
                    }
                }

                if (currentCoroutineContext().isActive) {
                    _connectionState.value = ConnectionState.Connecting
                    delay(reconnectDelayMs(failures))
                }
            }
        }

        if (currentCoroutineContext().isActive) {
            pollMessages(channel)
        }
    }

    /**
     * Abre una sesión realtime y suspende hasta que el socket se cierre.
     * [onSubscribed] se invoca cuando el servidor confirma la suscripción.
     */
    private suspend fun openRealtimeSession(
        channel: ResolvedChannel,
        onSubscribed: () -> Unit
    ) {
        val chatroomId = channel.chatroomId
            ?: throw IOException("Chatroom de Kick no resuelto")
        val socketUrl = fetchRealtimeSocketUrl(channel.channelId)
        val token = fetchConnectionToken()

        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(socketUrl)
                .header("User-Agent", userAgent)
                .header("Origin", "https://kick.com")
                .build()

            val listener = RealtimeListener(
                chatroomId = chatroomId,
                token = token,
                continuation = continuation,
                onSubscribed = onSubscribed
            )
            val socket = webSocketClient.newWebSocket(request, listener)
            continuation.invokeOnCancellation { socket.cancel() }
        }
    }

    /**
     * Pide al backend la URL del WebSocket y el proveedor de tiempo real.
     * Actualmente responde Centrifugo.
     */
    private suspend fun fetchRealtimeSocketUrl(channelId: String): String =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("client", JSONObject().apply {
                    put("id", clientId)
                    put("type", "web")
                })
                put("capabilities", JSONObject().apply {
                    put("accepted_providers", JSONArray().apply {
                        put(JSONObject().put("provider", "centrifugo"))
                    })
                })
            }

            val request = Request.Builder()
                .url("$KICK_WEB_API_BASE/v1/realtime/channels/$channelId/chat/connection")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("Origin", "https://kick.com")
                .header("Referer", "https://kick.com/")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Kick realtime respondió HTTP ${response.code}")
                }
                val root = JSONObject(response.body?.string().orEmpty())
                val data = root.optJSONObject("data")
                    ?: throw IOException("Kick realtime sin datos")
                if (data.optString("mode") != "websocket") {
                    throw IOException("Kick realtime en modo no soportado")
                }

                val connections = data.optJSONArray("connections")
                    ?: throw IOException("Kick realtime sin conexiones")
                for (i in 0 until connections.length()) {
                    val connection = connections.optJSONObject(i) ?: continue
                    if (connection.optString("provider") != "centrifugo") continue
                    val url = connection.optJSONObject("credentials")
                        ?.optString("url")
                        ?.takeIf { it.isNotBlank() }
                    if (url != null) return@withContext url
                }
                throw IOException("Kick realtime sin conexión Centrifugo")
            }
        }

    /** Token de invitado (JWT) que autoriza la conexión Centrifugo. */
    private suspend fun fetchConnectionToken(): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("client_id", clientId)
        val request = Request.Builder()
            .url("$KICK_WEB_API_BASE/v1/realtime/auth/connection")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Origin", "https://kick.com")
            .header("Referer", "https://kick.com/")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Kick auth respondió HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            root.optJSONObject("data")
                ?.optString("token")
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("Kick auth sin token")
        }
    }

    private fun reconnectDelayMs(failures: Int): Long {
        if (failures <= 0) return RECONNECT_DELAY_MS
        val exponent = (failures - 1).coerceIn(0, 5)
        return (1_000L shl exponent).coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    /** Respaldo por polling. Solo se usa si el realtime no está disponible. */
    private suspend fun pollMessages(channel: ResolvedChannel) {
        var firstFetch = true
        var consecutiveErrors = 0

        while (currentCoroutineContext().isActive) {
            val startedAt = System.nanoTime()
            var delayMs = POLL_INTERVAL_MS
            try {
                val batch = fetchMessages(channel.channelId)
                consecutiveErrors = 0

                if (firstFetch) {
                    // Sembramos el historial reciente sin inundar el chat al conectar.
                    firstFetch = false
                    synchronized(seenMessageIds) {
                        batch.forEach { seenMessageIds.add(it.id) }
                        trimSeenLocked()
                    }
                } else {
                    // La API devuelve del más nuevo al más antiguo.
                    batch.asReversed().forEach { message ->
                        if (markSeen(message.id)) {
                            _messageFlow.emit(message)
                        }
                    }
                }

                if (_connectionState.value !is ConnectionState.Connected) {
                    _connectionState.value = ConnectionState.Connected(channel.displayName)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveErrors++
                Log.w(TAG, "Fallo obteniendo mensajes de Kick (intento $consecutiveErrors): ${e.message}")
                if (consecutiveErrors == ERROR_THRESHOLD) {
                    _connectionState.value = ConnectionState.Error(
                        e.localizedMessage ?: "Sin conexión con Kick"
                    )
                }
                delayMs = backoffMs(consecutiveErrors)
            }

            // Cadencia fija: se descuenta el tiempo de red en lugar de sumarlo.
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            delay((delayMs - elapsedMs).coerceAtLeast(MIN_POLL_DELAY_MS))
        }
    }

    private suspend fun fetchMessages(channelId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://kick.com/api/v2/channels/$channelId/messages")
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://kick.com/")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Kick respondió HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@withContext emptyList()

            val messages = JSONObject(body)
                .optJSONObject("data")
                ?.optJSONArray("messages")
                ?: return@withContext emptyList()

            val result = ArrayList<ChatMessage>(messages.length())
            for (i in 0 until messages.length()) {
                val node = messages.optJSONObject(i) ?: continue
                result += parseMessage(node) ?: continue
            }
            result
        }
    }

    /** Convierte el JSON de un mensaje (polling o realtime) en [ChatMessage]. */
    private fun parseMessage(node: JSONObject): ChatMessage? {
        val content = parseContent(node.optString("content"))
        if (content.text.isBlank()) return null

        val sender = node.optJSONObject("sender")
        val username = sender?.optString("username")?.takeIf { it.isNotBlank() }
            ?: sender?.optString("slug")?.takeIf { it.isNotBlank() }
            ?: return null

        val messageId = node.optString("id").takeIf { it.isNotBlank() } ?: return null

        return ChatMessage(
            id = messageId,
            platform = Platform.KICK,
            username = username,
            message = content.text,
            timestamp = parseIsoTimestamp(node.optString("created_at")),
            userColor = parseIdentityColor(sender?.optJSONObject("identity"))
                ?: DEFAULT_KICK_COLOR,
            emotes = content.emotes
        )
    }

    /**
     * `App\Events\SubscriptionEvent` del realtime:
     * `{ chatroom_id, username, months }`.
     */
    internal fun parseSubscriptionEvent(node: JSONObject): ChatMessage? {
        val username = node.optString("username").takeIf { it.isNotBlank() } ?: return null
        val months = node.optInt("months", 1).coerceAtLeast(1)
        return ChatMessage(
            platform = Platform.KICK,
            username = username,
            message = "",
            timestamp = System.currentTimeMillis(),
            userColor = DEFAULT_KICK_COLOR,
            event = ChatEvent(type = ChatEventType.SUBSCRIPTION, count = months)
        )
    }

    /**
     * `App\Events\GiftedSubscriptionsEvent` del realtime:
     * `{ chatroom_id, gifter_username, gifted_usernames: [...] }`.
     */
    internal fun parseGiftedSubscriptionsEvent(node: JSONObject): ChatMessage? {
        val gifter = node.optString("gifter_username").takeIf { it.isNotBlank() } ?: return null
        val gifted = node.optJSONArray("gifted_usernames")
        val count = (gifted?.length() ?: 0).coerceAtLeast(1)
        val recipient = if (gifted != null && gifted.length() == 1) {
            gifted.optString(0).takeIf { it.isNotBlank() }
        } else {
            null
        }

        return ChatMessage(
            platform = Platform.KICK,
            username = gifter,
            message = "",
            timestamp = System.currentTimeMillis(),
            userColor = DEFAULT_KICK_COLOR,
            event = ChatEvent(
                type = ChatEventType.GIFT_SUBSCRIPTION,
                count = count,
                recipient = recipient
            )
        )
    }

    /** Kick envía emotes como `[emote:6406279:collectiblesHeels]`. */
    internal fun cleanContent(raw: String): String = parseContent(raw).text

    /**
     * Convierte los tokens de emote de Kick en texto legible (`:nombre:`) y devuelve
     * los rangos para que la UI pueda sustituirlos por la imagen del CDN.
     */
    internal fun parseContent(raw: String): ParsedKickContent {
        val text = StringBuilder(raw.length)
        val emotes = ArrayList<EmoteSpan>()
        var last = 0

        for (match in EMOTE_REGEX.findAll(raw)) {
            text.append(raw, last, match.range.first)
            val id = match.groupValues[1]
            val name = match.groupValues[2]
            val start = text.length
            text.append(':').append(name).append(':')
            emotes += EmoteSpan(
                start = start,
                end = text.length,
                name = name,
                url = emoteUrl(id)
            )
            last = match.range.last + 1
        }
        text.append(raw, last, raw.length)

        return trimWithSpans(text.toString(), emotes)
    }

    private fun trimWithSpans(text: String, spans: List<EmoteSpan>): ParsedKickContent {
        val firstVisible = text.indexOfFirst { !it.isWhitespace() }
        if (firstVisible == -1) return ParsedKickContent("", emptyList())

        val lastVisible = text.indexOfLast { !it.isWhitespace() } + 1
        val trimmed = text.substring(firstVisible, lastVisible)
        if (firstVisible == 0 && lastVisible == text.length) {
            return ParsedKickContent(trimmed, spans)
        }

        val adjusted = spans.mapNotNull { span ->
            val start = span.start - firstVisible
            val end = span.end - firstVisible
            if (start >= 0 && end <= trimmed.length) {
                span.copy(start = start, end = end)
            } else {
                null
            }
        }
        return ParsedKickContent(trimmed, adjusted)
    }

    internal fun emoteUrl(emoteId: String): String = "$KICK_EMOTE_URL_PREFIX$emoteId/fullsize"

    internal fun parseIdentityColor(identity: JSONObject?): Color? {
        val hex = identity?.optString("color")?.takeIf { it.startsWith("#") } ?: return null
        return try {
            val colorInt = hex.removePrefix("#").toLong(16).toInt()
            Color(colorInt or -0x1000000)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Kick entrega `2026-11-13T05:57:45Z` en el historial y
     * `2026-11-15T18:50:55+00:00` en el realtime.
     */
    internal fun parseIsoTimestamp(value: String): Long {
        if (value.isBlank()) return System.currentTimeMillis()
        return try {
            val normalized = value
                .replace(MILLIS_REGEX, "")
                .replace(OFFSET_WITH_COLON_REGEX, "$1$2")
                .replace(UTC_SUFFIX_REGEX, "+0000")
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(normalized)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun markSeen(id: String): Boolean {
        synchronized(seenMessageIds) {
            if (!seenMessageIds.add(id)) return false
            trimSeenLocked()
        }
        return true
    }

    private fun trimSeenLocked() {
        if (seenMessageIds.size <= MAX_SEEN_IDS) return
        val iterator = seenMessageIds.iterator()
        var toRemove = seenMessageIds.size - MAX_SEEN_IDS
        while (toRemove > 0 && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
            toRemove--
        }
    }

    private fun backoffMs(errors: Int): Long =
        (POLL_INTERVAL_MS * errors).coerceIn(POLL_INTERVAL_MS, 15_000L)

    /**
     * Puente entre los callbacks de OkHttp y las corrutinas.
     * El protocolo es el de Centrifugo en JSON, un comando por línea.
     */
    private inner class RealtimeListener(
        private val chatroomId: String,
        private val token: String,
        private val continuation: CancellableContinuation<Unit>,
        private val onSubscribed: () -> Unit
    ) : WebSocketListener() {

        private val commandIds = AtomicInteger(0)
        private val finished = AtomicBoolean(false)
        private var tokenRefreshJob: Job? = null

        override fun onOpen(webSocket: WebSocket, response: Response) {
            val command = JSONObject().apply {
                put("id", commandIds.incrementAndGet())
                put("connect", JSONObject().apply {
                    put("token", token)
                    put("name", CLIENT_NAME)
                    put("version", CLIENT_VERSION)
                })
            }
            webSocket.send(command.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            for (rawLine in text.split('\n')) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                if (line == PONG_FRAME) {
                    // Ping del servidor: Centrifugo espera un objeto vacío.
                    webSocket.send(PONG_FRAME)
                    continue
                }
                val frame = runCatching { JSONObject(line) }.getOrNull() ?: continue
                handleFrame(webSocket, frame)
            }
        }

        private fun handleFrame(webSocket: WebSocket, frame: JSONObject) {
            frame.optJSONObject("connect")?.let { connect ->
                val subscribe = JSONObject().apply {
                    put("id", commandIds.incrementAndGet())
                    put("subscribe", JSONObject().put("channel", "chatrooms.$chatroomId.v2"))
                }
                webSocket.send(subscribe.toString())
                if (connect.optBoolean("expires", false)) {
                    scheduleTokenRefresh(webSocket, connect.optLong("ttl", 0))
                }
            }

            frame.optJSONObject("subscribe")?.let {
                if (finished.get()) return
                Log.d(TAG, "Realtime de Kick suscrito a chatrooms.$chatroomId.v2")
                onSubscribed()
            }

            frame.optJSONObject("refresh")?.let { refresh ->
                if (refresh.optBoolean("expires", false)) {
                    scheduleTokenRefresh(webSocket, refresh.optLong("ttl", 0))
                }
            }

            frame.optJSONObject("push")?.let { push -> handlePush(push) }

            frame.optJSONObject("disconnect")?.let { disconnect ->
                Log.w(
                    TAG,
                    "Kick cerró el realtime (${disconnect.optInt("code")}): " +
                        disconnect.optString("reason")
                )
                webSocket.close(1000, null)
            }

            frame.optJSONObject("error")?.let { error ->
                Log.w(
                    TAG,
                    "Error del realtime de Kick (${error.optInt("code")}): " +
                        error.optString("message")
                )
            }
        }

        private fun handlePush(push: JSONObject) {
            val publication = push.optJSONObject("pub") ?: return
            val envelope = publication.optJSONObject("data") ?: return

            val payload = when (val raw = envelope.opt("data")) {
                is JSONObject -> raw
                is String -> runCatching { JSONObject(raw) }.getOrNull()
                else -> null
            } ?: return

            when (envelope.optString("event")) {
                CHAT_MESSAGE_EVENT -> {
                    val message = parseMessage(payload) ?: return
                    if (!markSeen(message.id)) return
                    _messageFlow.tryEmit(message)
                }
                SUBSCRIPTION_EVENT -> {
                    parseSubscriptionEvent(payload)?.let { _messageFlow.tryEmit(it) }
                }
                GIFTED_SUBSCRIPTIONS_EVENT -> {
                    parseGiftedSubscriptionsEvent(payload)?.let { _messageFlow.tryEmit(it) }
                }
            }
        }

        /**
         * El token de conexión caduca (ttl ≈ 45 min). Se renueva antes para no
         * tener que reconectar y arriesgar un hueco de mensajes.
         */
        private fun scheduleTokenRefresh(webSocket: WebSocket, ttlSeconds: Long) {
            tokenRefreshJob?.cancel()
            if (ttlSeconds <= 0) return
            val waitMs = ((ttlSeconds - TOKEN_REFRESH_MARGIN_S).coerceAtLeast(30L)) * 1_000L
            tokenRefreshJob = scope.launch {
                delay(waitMs)
                try {
                    val refreshed = fetchConnectionToken()
                    val command = JSONObject().apply {
                        put("id", commandIds.incrementAndGet())
                        put("refresh", JSONObject().put("token", refreshed))
                    }
                    webSocket.send(command.toString())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo renovar el token de Kick: ${e.message}")
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Realtime de Kick cerrado ($code): $reason")
            finish()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Realtime de Kick falló: ${t.message}")
            finish()
        }

        private fun finish() {
            if (!finished.compareAndSet(false, true)) return
            tokenRefreshJob?.cancel()
            tokenRefreshJob = null
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    companion object {
        private const val TAG = "KickChatClient"
        private const val POLL_INTERVAL_MS = 1_500L
        private const val MIN_POLL_DELAY_MS = 250L
        private const val ERROR_THRESHOLD = 3
        private const val MAX_SEEN_IDS = 600
        private const val KICK_EMOTE_URL_PREFIX = "https://files.kick.com/emotes/"
        private const val KICK_WEB_API_BASE = "https://web.kick.com/api"
        private const val CHAT_MESSAGE_EVENT = "App\\Events\\ChatMessageEvent"
        private const val SUBSCRIPTION_EVENT = "App\\Events\\SubscriptionEvent"
        private const val GIFTED_SUBSCRIPTIONS_EVENT = "App\\Events\\GiftedSubscriptionsEvent"
        private const val CLIENT_NAME = "js"
        private const val CLIENT_VERSION = "5.0.0"
        private const val PONG_FRAME = "{}"
        private const val TOKEN_REFRESH_MARGIN_S = 60L
        private const val MAX_REALTIME_ATTEMPTS = 3
        private const val RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private val DEFAULT_KICK_COLOR = Color(0xFF53FC18)
        private val EMOTE_REGEX = Regex("\\[emote:(\\d+):([^\\]]+)]")
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val MILLIS_REGEX = Regex("\\.\\d+")
        private val OFFSET_WITH_COLON_REGEX = Regex("([+-]\\d{2}):(\\d{2})$")
        private val UTC_SUFFIX_REGEX = Regex("Z$")
    }
}

internal data class ParsedKickContent(
    val text: String,
    val emotes: List<EmoteSpan>
)
