package com.example.chatmultistream.data.client

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.example.chatmultistream.model.ChatEvent
import com.example.chatmultistream.model.ChatEventType
import com.example.chatmultistream.model.ChatMessage
import com.example.chatmultistream.model.ConnectionState
import com.example.chatmultistream.model.EmoteSpan
import com.example.chatmultistream.model.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class TwitchChatClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket: sin timeout de lectura
        .pingInterval(20, TimeUnit.SECONDS)    // Detecta conexiones muertas
        .build()
) : ChatClient {

    override val platform: Platform = Platform.TWITCH

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messageFlow = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    override val messageFlow: SharedFlow<ChatMessage> = _messageFlow.asSharedFlow()

    private var currentWebSocket: WebSocket? = null

    private var targetChannel: String = ""
    private var targetToken: String? = null

    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    @Volatile
    private var shouldReconnect = false

    @Volatile
    private var isClosed = false

    override suspend fun connect(channelName: String, authToken: String?) {
        val cleanChannel = channelName.trim().removePrefix("#").lowercase()
        if (cleanChannel.isEmpty()) {
            _connectionState.value = ConnectionState.Error("Nombre de canal inválido")
            return
        }

        disconnect()
        _connectionState.value = ConnectionState.Connecting
        targetChannel = cleanChannel
        targetToken = authToken
        reconnectAttempt = 0
        shouldReconnect = true
        isClosed = false

        openSocket(cleanChannel, authToken)
    }

    private fun openSocket(channel: String, authToken: String?) {
        val request = Request.Builder()
            .url("wss://irc-ws.chat.twitch.tv:443")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== currentWebSocket) return
                reconnectAttempt = 0

                // Request capabilities for rich tags (badges, user colors, display names)
                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")

                if (!authToken.isNullOrBlank()) {
                    val token = if (authToken.startsWith("oauth:")) authToken else "oauth:$authToken"
                    webSocket.send("PASS $token")
                    webSocket.send("NICK $channel")
                } else {
                    // Anonymous connection to public chat
                    val randomId = (10000..99999).random()
                    webSocket.send("PASS SCHMOOPIE")
                    webSocket.send("NICK justinfan$randomId")
                }

                webSocket.send("JOIN #$channel")
                _connectionState.value = ConnectionState.Connected(channel)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.lines().forEach { line ->
                    handleIrcLine(webSocket, line)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== currentWebSocket) return
                if (shouldReconnect && !isClosed) {
                    scheduleReconnect(webSocket)
                } else {
                    _connectionState.value = ConnectionState.Error(t.localizedMessage ?: "Error de conexión")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== currentWebSocket) return
                if (shouldReconnect && !isClosed) {
                    scheduleReconnect(webSocket)
                } else {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }

        currentWebSocket = okHttpClient.newWebSocket(request, listener)
    }

    /**
     * Reconexión con backoff exponencial (1 s, 2 s, 4 s... máx. 30 s).
     * Twitch cierra el socket periódicamente y también lo reinicia con `RECONNECT`.
     */
    private fun scheduleReconnect(webSocket: WebSocket) {
        if (!shouldReconnect || isClosed) return
        if (webSocket !== currentWebSocket) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            val channel = targetChannel
            if (channel.isBlank()) return@launch

            reconnectAttempt++
            val waitMs = backoffMs(reconnectAttempt)
            Log.d(TAG, "Reconectando a Twitch en ${waitMs}ms (intento $reconnectAttempt)")
            _connectionState.value = ConnectionState.Connecting
            delay(waitMs)

            if (!shouldReconnect || isClosed) return@launch
            if (webSocket !== currentWebSocket) return@launch
            try {
                currentWebSocket?.cancel()
            } catch (_: Exception) {}
            openSocket(channel, targetToken)
        }
    }

    private fun backoffMs(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 5)
        return (1_000L shl exponent).coerceAtMost(MAX_BACKOFF_MS)
    }

    override suspend fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        try {
            currentWebSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        currentWebSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun close() {
        isClosed = true
        shouldReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            currentWebSocket?.close(1000, "Client released")
        } catch (_: Exception) {}
        currentWebSocket = null
        _connectionState.value = ConnectionState.Disconnected
        scope.cancel()
    }

    private fun handleIrcLine(webSocket: WebSocket, line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return

        // Twitch pide reconectar antes de cerrar el socket.
        if (!trimmed.contains("PRIVMSG") &&
            !trimmed.contains("USERNOTICE") &&
            trimmed.contains("RECONNECT")
        ) {
            scheduleReconnect(webSocket)
            return
        }

        // Respond to heartbeat PING to keep connection alive
        if (trimmed.startsWith("PING")) {
            val pongPayload = trimmed.removePrefix("PING").trim()
            webSocket.send("PONG $pongPayload")
            return
        }

        if (trimmed.contains("PRIVMSG")) {
            val message = parseIrcPrivmsg(trimmed)
            if (message != null) {
                scope.launch {
                    _messageFlow.emit(message)
                }
            }
        } else if (trimmed.contains("USERNOTICE")) {
            val notice = parseUserNotice(trimmed)
            if (notice != null) {
                scope.launch {
                    _messageFlow.emit(notice)
                }
            }
        }
    }

    /**
     * Separa los tags IRC (`@clave=valor;...`) del resto de la línea.
     * Los tags que llegan vacíos se conservan como cadena vacía, porque algunos
     * (por ejemplo `msg-id`) se comprueban directamente.
     */
    private fun extractTags(rawLine: String): Pair<Map<String, String>, String> {
        if (!rawLine.startsWith("@")) return emptyMap<String, String>() to rawLine
        val spaceIndex = rawLine.indexOf(' ')
        if (spaceIndex == -1) return emptyMap<String, String>() to rawLine

        val tags = rawLine.substring(1, spaceIndex).split(";").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
        return tags to rawLine.substring(spaceIndex + 1)
    }

    internal fun parseIrcPrivmsg(rawLine: String): ChatMessage? {
        return try {
            val (tagsMap, contentPart) = extractTags(rawLine)

            val privmsgIndex = contentPart.indexOf("PRIVMSG")
            if (privmsgIndex == -1) return null

            // Extract message body after "PRIVMSG #channel :"
            val colonIndex = contentPart.indexOf(':', privmsgIndex)
            var messageText = if (colonIndex != -1) {
                contentPart.substring(colonIndex + 1)
            } else {
                ""
            }

            // Extract username from prefix :username!username@...
            val userPrefix = contentPart.substring(0, privmsgIndex).trim()
            val rawNick = if (userPrefix.startsWith(":")) {
                val exclamation = userPrefix.indexOf('!')
                if (exclamation != -1) userPrefix.substring(1, exclamation) else userPrefix.removePrefix(":")
            } else {
                "anonymous"
            }

            val displayName = tagsMap["display-name"]?.takeIf { it.isNotBlank() } ?: rawNick
            val colorHex = tagsMap["color"]
            val userColor = parseHexColor(colorHex)

            var emotes = parseEmoteSpans(tagsMap["emotes"].orEmpty(), messageText)
            val bits = tagsMap["bits"]?.toIntOrNull() ?: 0
            if (bits > 0) {
                // Twitch incluye el propio "Cheer100" al principio del texto;
                // la tarjeta ya muestra los bits en su etiqueta.
                val cheer = CHEER_PREFIX_REGEX.find(messageText)
                if (cheer != null) {
                    val removed = cheer.value.length
                    messageText = messageText.substring(removed)
                    emotes = emotes.mapNotNull { span ->
                        val start = span.start - removed
                        val end = span.end - removed
                        if (start >= 0 && end <= messageText.length) {
                            span.copy(start = start, end = end)
                        } else {
                            null
                        }
                    }
                }
            }

            val event = when {
                !tagsMap["custom-reward-id"].isNullOrBlank() ->
                    ChatEvent(ChatEventType.REDEMPTION)
                bits > 0 -> ChatEvent(ChatEventType.CHEER, count = bits)
                tagsMap["msg-id"] == "highlighted-message" ->
                    ChatEvent(ChatEventType.HIGHLIGHT)
                else -> null
            }

            ChatMessage(
                platform = Platform.TWITCH,
                username = displayName,
                message = messageText,
                timestamp = System.currentTimeMillis(),
                userColor = userColor,
                emotes = emotes,
                event = event
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Avisos de Twitch que no llegan como `PRIVMSG`: suscripciones, regalos,
     * raids y anuncios. Twitch los entrega en `USERNOTICE` con los datos en
     * tags `msg-param-*`.
     */
    internal fun parseUserNotice(rawLine: String): ChatMessage? {
        return try {
            val (tagsMap, contentPart) = extractTags(rawLine)

            val noticeIndex = contentPart.indexOf("USERNOTICE")
            if (noticeIndex == -1) return null

            val colonIndex = contentPart.indexOf(':', noticeIndex)
            val messageText = if (colonIndex != -1) {
                contentPart.substring(colonIndex + 1)
            } else {
                ""
            }

            val username = tagsMap["display-name"]?.takeIf { it.isNotBlank() }
                ?: tagsMap["login"]?.takeIf { it.isNotBlank() }
                ?: "Twitch"

            val event = when (tagsMap["msg-id"]) {
                "sub", "resub" -> ChatEvent(
                    type = ChatEventType.SUBSCRIPTION,
                    count = tagsMap["msg-param-cumulative-months"]?.toIntOrNull()
                        ?: tagsMap["msg-param-months"]?.toIntOrNull()
                        ?: 1
                )
                "subgift" -> ChatEvent(
                    type = ChatEventType.GIFT_SUBSCRIPTION,
                    count = 1,
                    recipient = tagsMap["msg-param-recipient-display-name"]
                        ?.takeIf { it.isNotBlank() }
                )
                "submysterygift" -> ChatEvent(
                    type = ChatEventType.GIFT_SUBSCRIPTION,
                    count = tagsMap["msg-param-mass-gift-count"]?.toIntOrNull() ?: 1
                )
                "raid" -> ChatEvent(
                    type = ChatEventType.RAID,
                    count = tagsMap["msg-param-viewerCount"]?.toIntOrNull() ?: 0
                )
                "announcement" -> ChatEvent(ChatEventType.ANNOUNCEMENT)
                else -> null
            } ?: return null

            ChatMessage(
                platform = Platform.TWITCH,
                username = username,
                message = messageText,
                timestamp = System.currentTimeMillis(),
                userColor = parseHexColor(tagsMap["color"]),
                emotes = parseEmoteSpans(tagsMap["emotes"].orEmpty(), messageText),
                event = event
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * El tag `emotes` de Twitch tiene el formato:
     * `25:0-4,12-16/emotesv2_9035...:6-10`.
     *
     * Los IDs pueden ser numéricos (emotes globales/antiguos) o empezar por
     * `emotesv2_` (emotes de suscripción y otros emotes nuevos). Los índices
     * son code points Unicode, por eso se convierten a índices UTF-16.
     */
    internal fun parseEmoteSpans(emotesTag: String, messageText: String): List<EmoteSpan> {
        if (emotesTag.isBlank() || messageText.isEmpty()) return emptyList()

        val spans = ArrayList<EmoteSpan>()
        for (entry in emotesTag.split('/')) {
            val colon = entry.indexOf(':')
            if (colon <= 0) continue

            val id = entry.substring(0, colon)
            if (!isValidEmoteId(id)) continue

            for (range in entry.substring(colon + 1).split(',')) {
                val dash = range.indexOf('-')
                if (dash <= 0) continue

                val startCodePoint = range.substring(0, dash).toIntOrNull() ?: continue
                val endCodePoint = range.substring(dash + 1).toIntOrNull() ?: continue
                val start = codePointToCharIndex(messageText, startCodePoint)
                val end = codePointToCharIndex(messageText, endCodePoint + 1)

                if (start >= 0 && end > start && end <= messageText.length) {
                    spans += EmoteSpan(
                        start = start,
                        end = end,
                        name = messageText.substring(start, end),
                        url = emoteUrl(id)
                    )
                }
            }
        }
        return spans.sortedBy { it.start }
    }

    /**
     * Se pide la variante `static` a propósito: `default` devuelve un GIF para
     * los emotes animados y Coil (sin `coil-gif`) no podría decodificarlo.
     * Además el PNG gasta menos CPU y memoria, que es el objetivo de la app.
     */
    internal fun emoteUrl(emoteId: String): String = "$TWITCH_EMOTE_URL_PREFIX$emoteId/static/dark/2.0"

    /**
     * Acepta IDs numéricos y los nuevos IDs `emotesv2_...` que Twitch usa para
     * emotes de suscripción, bits y otros emotes recientes.
     */
    private fun isValidEmoteId(id: String): Boolean =
        id.isNotEmpty() && id.length <= 64 && id.all { it.isLetterOrDigit() || it == '_' }

    private fun codePointToCharIndex(text: String, codePointIndex: Int): Int {
        var charIndex = 0
        var codePoints = 0
        while (charIndex < text.length && codePoints < codePointIndex) {
            charIndex += java.lang.Character.charCount(text.codePointAt(charIndex))
            codePoints++
        }
        return if (codePoints == codePointIndex) charIndex else -1
    }

    private fun parseHexColor(hex: String?): Color? {
        if (hex.isNullOrBlank() || !hex.startsWith("#")) return null
        return try {
            val cleanHex = hex.removePrefix("#")
            val colorInt = cleanHex.toLong(16).toInt()
            Color(colorInt or -0x1000000)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "TwitchChatClient"
        private const val MAX_BACKOFF_MS = 30_000L
        private const val TWITCH_EMOTE_URL_PREFIX = "https://static-cdn.jtvnw.net/emoticons/v2/"
        private val CHEER_PREFIX_REGEX = Regex("^[Cc]heer\\d+\\s?")
    }
}
