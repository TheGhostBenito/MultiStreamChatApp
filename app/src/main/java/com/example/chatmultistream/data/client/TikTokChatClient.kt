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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Cliente de chat de TikTok.
 *
 * El modo nativo (sin login, sin firma y sin API key) se apoya en:
 *  1. `GET /api-live/user/room?...&sourceType=54` para resolver el `roomId` y el estado.
 *  2. Una cookie anónima `ttwid` obtenida de la portada de tiktok.com o del
 *     registro anónimo de ByteDance.
 *  3. Un WebSocket a `/webcast/im/ws_proxy/ws_reuse_supplement/`, que acepta la
 *     conexión sin `signature` y entrega las tramas protobuf con el chat.
 *
 * El antiguo long-poll de `/webcast/im/fetch/` ya no sirve: TikTok responde
 * `200` con cuerpo vacío a cualquier petición sin firma.
 *
 * También se admite un relay WebSocket externo (TikTok-Live-Connector /
 * EulerStream) indicándolo en el campo de token.
 */
class TikTokChatClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)   // im/fetch es long-poll
        .callTimeout(40, TimeUnit.SECONDS)
        .build()
) : ChatClient {

    override val platform: Platform = Platform.TIKTOK

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messageFlow = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 128)
    override val messageFlow: SharedFlow<ChatMessage> = _messageFlow.asSharedFlow()

    private var currentWebSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    @Volatile
    private var reconnectAttempt = 0

    @Volatile
    private var shouldReconnect = false

    @Volatile
    private var isClosed = false

    @Volatile
    private var lastFrameAt = 0L

    private var activeSession: WebcastSession? = null

    /**
     * Claves de mensajes ya publicados (chat y regalos).
     *
     * TikTok reenvía bloques al reconectar, así que sin esta memoria se
     * verían duplicados. `LinkedHashSet` conserva el orden de inserción, lo
     * que permite expulsar primero las claves más antiguas.
     */
    private val seenKeys = LinkedHashSet<String>()

    @Volatile
    private var ttwid: String? = null

    private data class WebcastSession(
        val roomId: String,
        val cookie: String,
        val username: String
    )

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private data class TikTokRoomInfo(
        val roomId: String,
        val isLive: Boolean
    )

    internal data class ParsedChat(
        val dedupeKey: String?,
        val message: ChatMessage
    )

    override suspend fun connect(channelName: String, authToken: String?) {
        val cleanUser = sanitizeInput(channelName)
        if (cleanUser.isEmpty()) {
            _connectionState.value = ConnectionState.Error("Usuario o Room ID de TikTok no válido")
            return
        }

        disconnect()
        shouldReconnect = true
        isClosed = false
        reconnectAttempt = 0
        _connectionState.value = ConnectionState.Connecting

        // Modo relay: el usuario pega una URL WebSocket (TikTok-Live-Connector / EulerStream).
        if (!authToken.isNullOrBlank() && (authToken.startsWith("ws://") || authToken.startsWith("wss://"))) {
            Log.d(TAG, "Conectando mediante relay WebSocket")
            connectToRelayWebSocket(authToken.trim(), cleanUser)
            return
        }

        val roomInfo = try {
            resolveTikTokRoom(cleanUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error resolviendo la sala de TikTok: ${e.message}")
            null
        }

        if (roomInfo == null) {
            _connectionState.value = ConnectionState.Error(
                "No se pudo conectar con TikTok para @$cleanUser. Verifica el usuario."
            )
            return
        }

        if (!roomInfo.isLive) {
            _connectionState.value = ConnectionState.Error(
                "@$cleanUser no está transmitiendo en vivo en este momento."
            )
            return
        }

        val cookie = ensureTtwid()
        if (cookie.isNullOrBlank()) {
            _connectionState.value = ConnectionState.Error(
                "TikTok no autorizó la sesión anónima. Reintenta o usa un relay."
            )
            return
        }

        Log.d(TAG, "Abriendo WebSocket de TikTok roomId=${roomInfo.roomId} para @$cleanUser")
        activeSession = WebcastSession(roomInfo.roomId, cookie, cleanUser)
        openWebcastSocket()
    }

    override suspend fun disconnect() {
        shouldReconnect = false
        isClosed = false
        reconnectJob?.cancel()
        reconnectJob = null
        stopHeartbeat()
        try {
            currentWebSocket?.close(1000, "Desconectado por usuario")
        } catch (_: Exception) {}
        currentWebSocket = null
        activeSession = null
        reconnectAttempt = 0
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun close() {
        isClosed = true
        shouldReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        stopHeartbeat()
        try {
            currentWebSocket?.close(1000, "Cliente liberado")
        } catch (_: Exception) {}
        currentWebSocket = null
        activeSession = null
        _connectionState.value = ConnectionState.Disconnected
        scope.cancel()
    }

    internal fun sanitizeInput(raw: String): String =
        raw.trim()
            .removePrefix("@")
            .removePrefix("https://www.tiktok.com/@")
            .removePrefix("https://tiktok.com/@")
            .trimEnd('/')
            .substringAfterLast('/')

    /**
     * Cookie anónima `ttwid`: sin ella /webcast/im/fetch/ devuelve 403.
     * No requiere cuenta ni login.
     *
     * Se intentan dos fuentes independientes:
     *  1. La portada de tiktok.com (`Set-Cookie`).
     *  2. El registro anónimo de ByteDance, que emite un `ttwid` válido aunque la
     *     portada sirva una página anti-bot sin cookie.
     */
    private suspend fun ensureTtwid(): String? = withContext(Dispatchers.IO) {
        ttwid?.let { return@withContext it }

        ttwid = fetchHomepageTtwid() ?: fetchRegisteredTtwid()
        ttwid
    }

    private fun fetchHomepageTtwid(): String? = try {
        val request = Request.Builder()
            .url("https://www.tiktok.com/")
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val cookie = extractTtwid(response.headers.values("Set-Cookie"))
            Log.d(TAG, "ttwid desde portada: HTTP ${response.code}, cookie=${cookie != null}")
            cookie
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo obtener ttwid de la portada: ${e.message}")
        null
    }

    /**
     * `aid=1768` es la única combinación que acepta el registro sin firma.
     * Devuelve la cookie para el dominio bytedance.com, que aquí reenviamos
     * manualmente a webcast.tiktok.com.
     */
    private fun fetchRegisteredTtwid(): String? = try {
        val payload = JSONObject().apply {
            put("region", "us")
            put("aid", 1768)
            put("needFid", false)
            put("service", "www.tiktok.com")
            put("migrate_info", JSONObject().apply {
                put("ticket", "")
                put("source", "node")
            })
            put("cbUrlProtocol", "https")
            put("union", true)
        }

        val request = Request.Builder()
            .url("https://ttwid.bytedance.com/ttwid/union/register/")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val cookie = extractTtwid(response.headers.values("Set-Cookie"))
            Log.d(TAG, "ttwid desde registro: HTTP ${response.code}, cookie=${cookie != null}")
            cookie
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo registrar ttwid anónimo: ${e.message}")
        null
    }

    internal fun extractTtwid(setCookieHeaders: List<String>): String? {
        setCookieHeaders.forEach { header ->
            val index = header.indexOf("ttwid=", ignoreCase = true)
            if (index >= 0) {
                val value = header.substring(index + "ttwid=".length)
                    .substringBefore(';')
                    .trim()
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    /** Aprovecha cualquier llamada a tiktok.com que ya traiga la cookie `ttwid`. */
    private fun captureTtwidFromHeaders(setCookieHeaders: List<String>) {
        if (!ttwid.isNullOrBlank()) return
        extractTtwid(setCookieHeaders)?.let { ttwid = it }
    }

    private suspend fun resolveTikTokRoom(username: String): TikTokRoomInfo? = withContext(Dispatchers.IO) {
        // 1. El usuario pegó un roomId numérico directamente.
        if (username.all { it.isDigit() } && username.length >= 10) {
            return@withContext TikTokRoomInfo(roomId = username, isLive = true)
        }

        // 2. API pública de sala (la que ya funcionaba para detectar el directo).
        try {
            val url = "https://www.tiktok.com/api-live/user/room".toHttpUrl().newBuilder()
                .addQueryParameter("aid", "1988")
                .addQueryParameter("sourceType", "54")
                .addQueryParameter("uniqueId", username)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.tiktok.com/")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                captureTtwidFromHeaders(response.headers.values("Set-Cookie"))
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val data = JSONObject(body).optJSONObject("data")
                    val userObj = data?.optJSONObject("user")
                    val liveRoomObj = data?.optJSONObject("liveRoom")

                    val status = liveRoomObj?.optInt("status", userObj?.optInt("status", 4) ?: 4) ?: 4
                    val roomId = liveRoomObj?.optString("roomId")?.takeIf { it.isNotBlank() }
                        ?: userObj?.optString("roomId")?.takeIf { it.isNotBlank() }

                    if (roomId != null) {
                        val isLive = status == 2
                        Log.d(TAG, "API TikTok: @$username roomId=$roomId status=$status")
                        return@withContext TikTokRoomInfo(roomId = roomId, isLive = isLive)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error consultando API TikTok: ${e.message}")
        }

        // 3. Fallback: scraping conservador. Solo damos por "en vivo" si la página lo afirma.
        try {
            val request = Request.Builder()
                .url("https://www.tiktok.com/@$username/live")
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.tiktok.com/")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                captureTtwidFromHeaders(response.headers.values("Set-Cookie"))
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    val roomMatcher = Pattern.compile(""""roomId":"(\d+)"""").matcher(html)
                    val statusMatcher = Pattern.compile(""""status":(\d+)""").matcher(html)
                    if (roomMatcher.find()) {
                        val roomId = roomMatcher.group(1)
                        val status = if (statusMatcher.find()) statusMatcher.group(1)?.toIntOrNull() else null
                        if (roomId != null && status == 2) {
                            return@withContext TikTokRoomInfo(roomId = roomId, isLive = true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error en scrape de TikTok: ${e.message}")
        }

        null
    }

    // ---------------------------------------------------------------- WebSocket

    /**
     * TikTok acepta este endpoint sin `signature` ni API key: basta la cookie
     * `ttwid` y el `room_id`. El chat llega en tramas protobuf `WebcastPushFrame`.
     */
    private fun buildWebcastUrl(roomId: String): String {
        val url = "https://webcast-ws.tiktok.com/webcast/im/ws_proxy/ws_reuse_supplement/"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("version_code", "180800")
            .addQueryParameter("device_platform", "web")
            .addQueryParameter("cookie_enabled", "true")
            .addQueryParameter("screen_width", "1920")
            .addQueryParameter("screen_height", "1080")
            .addQueryParameter("browser_language", "en-US")
            .addQueryParameter("browser_platform", "Linux x86_64")
            .addQueryParameter("browser_name", "Mozilla")
            .addQueryParameter("browser_version", "5.0 (X11)")
            .addQueryParameter("browser_online", "true")
            .addQueryParameter("tz_name", "America/New_York")
            .addQueryParameter("app_name", "tiktok_web")
            .addQueryParameter("sup_ws_ds_opt", "1")
            .addQueryParameter("update_version_code", "2.0.0")
            .addQueryParameter("compress", "gzip")
            .addQueryParameter("webcast_language", "en")
            .addQueryParameter("ws_direct", "1")
            .addQueryParameter("aid", "1988")
            .addQueryParameter("live_id", "12")
            .addQueryParameter("app_language", "en")
            .addQueryParameter("client_enter", "1")
            .addQueryParameter("room_id", roomId)
            .addQueryParameter("identity", "audience")
            .addQueryParameter("history_comment_count", "6")
            .addQueryParameter("last_rtt", "150.000")
            .addQueryParameter("heartbeat_duration", HEARTBEAT_INTERVAL_MS.toString())
            .addQueryParameter("resp_content_type", "protobuf")
            .addQueryParameter("did_rule", "3")
            .build()

        return url.toString().replaceFirst("https://", "wss://")
    }

    private fun openWebcastSocket() {
        val session = activeSession ?: return

        val request = Request.Builder()
            .url(buildWebcastUrl(session.roomId))
            .header("User-Agent", userAgent)
            .header("Cookie", "ttwid=${session.cookie}")
            .header("Origin", "https://www.tiktok.com")
            .header("Referer", "https://www.tiktok.com/")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== currentWebSocket) return
                reconnectAttempt = 0
                lastFrameAt = System.currentTimeMillis()

                // El servidor espera heartbeat + enter para empezar a emitir.
                webSocket.send(WebcastProto.buildHeartbeat(session.roomId).toByteString())
                webSocket.send(WebcastProto.buildEnterRoom(session.roomId).toByteString())

                startHeartbeat(webSocket, session.roomId)
                _connectionState.value = ConnectionState.Connected("@${session.username} (En Vivo)")
                Log.d(TAG, "WebSocket de TikTok abierto (roomId=${session.roomId})")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (webSocket !== currentWebSocket) return
                lastFrameAt = System.currentTimeMillis()
                handleWebcastFrame(webSocket, bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastFrameAt = System.currentTimeMillis()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== currentWebSocket) return
                stopHeartbeat()
                Log.w(TAG, "Fallo del WebSocket TikTok (HTTP ${response?.code}): ${t.message}")
                if (shouldReconnect && !isClosed) {
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionState.Error(
                        t.localizedMessage ?: "Sin conexión con TikTok"
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== currentWebSocket) return
                stopHeartbeat()
                if (shouldReconnect && !isClosed) {
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }

        currentWebSocket = okHttpClient.newWebSocket(request, listener)
    }

    /**
     * Procesa una trama binaria del WebSocket.
     *
     * Solo las tramas `msg` traen mensajes de chat: las de tipo `hb` son
     * latidos y las de control no aportan contenido. Un error de
     * decodificación se registra y se descarta esa trama, de modo que un
     * mensaje corrupto no tumbe la conexión ni impida procesar los siguientes.
     */
    private fun handleWebcastFrame(webSocket: WebSocket, raw: ByteArray) {
        val frame = try {
            WebcastProto.decodeFrame(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Trama TikTok inválida: ${e.message}")
            return
        } ?: return

        if (frame.payloadType != "msg") return

        val response = try {
            WebcastProto.decodeResponse(frame.payload)
        } catch (e: Exception) {
            Log.w(TAG, "Respuesta TikTok inválida: ${e.message}")
            return
        } ?: return

        // Si el servidor pide ACK y no se responde, reenvía el mismo bloque
        // al reconectar. Se devuelven el logId y el internalExt recibidos.
        if (response.needsAck && response.internalExt.isNotEmpty()) {
            try {
                webSocket.send(WebcastProto.buildAck(frame.logId, response.internalExt).toByteString())
            } catch (_: Exception) {}
        }

        response.messages.forEach { message ->
            // Coincidencia exacta: `WebcastGiftBroadcastMessage` u otros
            // métodos con "Gift" en el nombre tienen otra estructura;
            // decodificarlos aquí produciría tarjetas falsas o vacías.
            if (message.method == "WebcastGiftMessage") {
                val gift = try {
                    WebcastProto.decodeGift(message.payload, message.msgId)
                } catch (e: Exception) {
                    Log.w(TAG, "Regalo TikTok inválido: ${e.message}")
                    null
                }
                if (gift != null) emitGift(gift)
                return@forEach
            }

            // `contains` cubre las variantes del servidor que llevan
            // "ChatMessage" en el nombre; `decodeChat` descarta las cargas sin
            // texto, así que los mensajes que no son chat no llegan a la UI.
            if (!message.method.contains("ChatMessage")) return@forEach

            val chat = try {
                WebcastProto.decodeChat(message.payload, message.msgId)
            } catch (e: Exception) {
                Log.w(TAG, "Chat TikTok inválido: ${e.message}")
                return@forEach
            } ?: return@forEach

            emitChat(chat)
        }
    }

    /**
     * Publica un chat normal.
     *
     * TikTok reenvía mensajes al reconectar o cuando se pierde un ACK, así
     * que se deduplica por id. Si el servidor no envía id, se usa una clave
     * compuesta por autor, hora y texto: distingue mensajes legítimos iguales
     * enviados en momentos distintos sin necesitar estado adicional.
     */
    private fun emitChat(chat: WebcastProto.Chat) {
        val key = if (chat.msgId != 0L) {
            chat.msgId.toString()
        } else {
            "${chat.nickname}|${chat.createTime}|${chat.content}"
        }

        // `seenKeys` es acotado (ver trimSeen): una sesión larga no debe
        // acumular ids indefinidamente. El bloqueo es defensivo: la colección
        // es mutable y su coste sin contención es despreciable frente a la
        // decodificación de la trama.
        synchronized(seenKeys) {
            if (!seenKeys.add(key)) return
            trimSeen(seenKeys)
        }

        val timestamp = normalizeTimestamp(chat.createTime)

        _messageFlow.tryEmit(
            ChatMessage(
                platform = Platform.TIKTOK,
                username = chat.nickname.ifBlank { chat.uniqueId }.ifBlank { "TikTokViewer" },
                message = chat.content,
                timestamp = timestamp,
                userColor = DEFAULT_TIKTOK_COLOR
            )
        )
    }

    /**
     * Decide si un `WebcastGiftMessage` debe convertirse en tarjeta.
     *
     * Los regalos con combo (las rosas, por ejemplo) generan un mensaje por
     * cada toque: ×1, ×2, ×3… Publicarlos todos llenaría el chat de tarjetas
     * casi idénticas y el usuario perdería el hilo de la conversación. Solo se
     * publica el mensaje que cierra el combo (`repeatEnd == 1`), que ya trae
     * el total acumulado. Los regalos sin combo llegan una sola vez y se
     * publican siempre.
     *
     * Es `internal` para poder probarlo sin abrir un WebSocket real.
     */
    internal fun shouldEmitGift(gift: WebcastProto.Gift): Boolean =
        gift.repeatEnd == 1 || !gift.combo

    /**
     * Publica un regalo como suceso destacado.
     *
     * La tarjeta usa el nombre que entrega TikTok (`Rosa`, `Galaxia`…) y la
     * cantidad; el texto del mensaje va vacío porque la interfaz compone la
     * etiqueta y así puede traducirla. El prefijo `gift:` evita que el id de
     * un regalo colisione con el de un chat, que comparten espacio numérico.
     */
    private fun emitGift(gift: WebcastProto.Gift) {
        if (!shouldEmitGift(gift)) return

        // `comboCount` es el paso actual y `repeatCount` el acumulado; si
        // ninguno viene informado, se asume un único regalo.
        val count = when {
            gift.comboCount > 0 -> gift.comboCount
            gift.repeatCount > 0 -> gift.repeatCount
            else -> 1
        }

        val key = if (gift.msgId != 0L) {
            "gift:${gift.msgId}"
        } else {
            "gift:${gift.nickname}|${gift.createTime}|${gift.giftName}|$count"
        }

        synchronized(seenKeys) {
            if (!seenKeys.add(key)) return
            trimSeen(seenKeys)
        }

        _messageFlow.tryEmit(
            ChatMessage(
                platform = Platform.TIKTOK,
                username = gift.nickname.ifBlank { gift.uniqueId }.ifBlank { "TikTokViewer" },
                message = "",
                timestamp = normalizeTimestamp(gift.createTime),
                userColor = DEFAULT_TIKTOK_COLOR,
                event = ChatEvent(
                    type = ChatEventType.GIFT,
                    count = count,
                    giftName = gift.giftName
                )
            )
        )
    }

    /**
     * Lleva la marca de tiempo de TikTok a milisegundos.
     *
     * Según el mensaje y la versión del servidor, `createTime` llega en
     * segundos, milisegundos o microsegundos. Se distingue por magnitud: una
     * fecha actual tiene 10 cifras en segundos, 13 en milisegundos y 16 en
     * microsegundos. Si viene vacía se usa la hora local.
     */
    private fun normalizeTimestamp(createTime: Long): Long = when {
        createTime <= 0L -> System.currentTimeMillis()
        createTime > 1_000_000_000_000_000L -> createTime / 1000   // microsegundos
        createTime > 1_000_000_000_000L -> createTime              // milisegundos
        else -> createTime * 1000                                  // segundos
    }

    private fun startHeartbeat(webSocket: WebSocket, roomId: String) {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            val heartbeat = WebcastProto.buildHeartbeat(roomId).toByteString()
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (webSocket !== currentWebSocket) return@launch

                val idleFor = System.currentTimeMillis() - lastFrameAt
                if (idleFor > STALE_TIMEOUT_MS) {
                    Log.w(TAG, "WebSocket TikTok inactivo ${idleFor}ms; reconectando")
                    try {
                        webSocket.cancel()
                    } catch (_: Exception) {}
                    scheduleReconnect()
                    return@launch
                }

                try {
                    webSocket.send(heartbeat)
                } catch (_: Exception) {
                    return@launch
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /** Backoff exponencial 1 s → 2 s → 4 s… máx. 30 s, igual que Twitch y YouTube. */
    private fun scheduleReconnect() {
        if (!shouldReconnect || isClosed) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            reconnectAttempt++
            if (reconnectAttempt > MAX_RECONNECT_ATTEMPTS) {
                shouldReconnect = false
                _connectionState.value = ConnectionState.Error(
                    "TikTok no respondió tras varios intentos. Reintenta en unos minutos."
                )
                return@launch
            }

            val waitMs = backoffMs(reconnectAttempt)
            Log.d(TAG, "Reconectando TikTok en ${waitMs}ms (intento $reconnectAttempt)")
            _connectionState.value = ConnectionState.Connecting
            delay(waitMs)

            if (!shouldReconnect || isClosed) return@launch

            val session = activeSession ?: return@launch
            val roomInfo = try {
                resolveTikTokRoom(session.username)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            if (roomInfo == null || !roomInfo.isLive) {
                Log.w(TAG, "La sala de TikTok ya no está en vivo")
                shouldReconnect = false
                _connectionState.value = ConnectionState.Error(
                    "El directo de TikTok terminó o ya no está disponible."
                )
                return@launch
            }

            val cookie = try {
                ensureTtwid()
            } catch (e: Exception) {
                null
            } ?: session.cookie

            activeSession = WebcastSession(roomInfo.roomId, cookie, session.username)
            lastFrameAt = System.currentTimeMillis()

            // Libera el guard antes de abrir: si el handshake falla, onFailure
            // podrá volver a programar la reconexión.
            reconnectJob = null
            openWebcastSocket()
        }
    }

    private fun backoffMs(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 5)
        return (1_000L shl exponent).coerceAtMost(MAX_BACKOFF_MS)
    }

    /**
     * Soporta los dos formatos que devuelve TikTok en modo JSON
     * (`messages[].payload` y `messages[]` plano). Se ignoran eventos que no son chat.
     */
    internal fun parseEntry(entry: JSONObject): ParsedChat? {
        val method = entry.optString("method")
        if (method.isNotBlank() && !method.contains("chat", ignoreCase = true)) return null

        val payload = entry.optJSONObject("payload") ?: entry

        val content = payload.optString("content")
            .ifBlank { payload.optString("text") }
            .ifBlank { entry.optString("content") }
            .ifBlank { entry.optString("comment") }
            .trim()
        if (content.isBlank()) return null

        val username = resolveUsername(payload)
        val timestamp = resolveTimestamp(payload)
        val id = payload.optString("id").ifBlank { entry.optString("id") }.ifBlank { entry.optString("msgId") }

        val dedupeKey = id.takeIf { it.isNotBlank() }
            ?: "$username|$timestamp|$content"

        return ParsedChat(
            dedupeKey = dedupeKey,
            message = ChatMessage(
                platform = Platform.TIKTOK,
                username = username,
                message = content,
                timestamp = timestamp,
                userColor = DEFAULT_TIKTOK_COLOR
            )
        )
    }

    internal fun resolveUsername(payload: JSONObject): String {
        val userValue = payload.opt("user")
        val candidate = when (userValue) {
            is JSONObject -> userValue.optString("nickname").ifBlank { userValue.optString("uniqueId") }
            is String -> userValue
            else -> payload.optString("nickname").ifBlank { payload.optString("uniqueId") }
        }
        return candidate.ifBlank { "TikTokViewer" }
    }

    /** Igual que [normalizeTimestamp], pero para el modo relay JSON. */
    internal fun resolveTimestamp(payload: JSONObject): Long {
        val raw = payload.optLong("createTime", 0L)
            .takeIf { it > 0 }
            ?: payload.optLong("create_time", 0L)
        return when {
            raw <= 0L -> System.currentTimeMillis()
            raw > 1_000_000_000_000_000L -> raw / 1000      // microsegundos
            raw > 1_000_000_000_000L -> raw                 // milisegundos
            else -> raw * 1000                              // segundos
        }
    }

    /**
     * Mantiene [seenKeys] acotado eliminando las claves más antiguas.
     *
     * Una sesión larga puede generar decenas de miles de mensajes; recordar
     * solo los últimos ids cubre de sobra los reenvíos cercanos y mantiene el
     * uso de memoria constante.
     */
    private fun trimSeen(seen: MutableSet<String>) {
        if (seen.size <= MAX_SEEN_KEYS) return
        val iterator = seen.iterator()
        var toRemove = seen.size - MAX_SEEN_KEYS
        while (toRemove > 0 && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
            toRemove--
        }
    }

    private fun connectToRelayWebSocket(relayUrl: String, username: String) {
        val request = Request.Builder()
            .url(relayUrl)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.Connected("@$username (Relay)")
                val joinJson = JSONObject().apply {
                    put("event", "join")
                    put("username", username)
                }
                webSocket.send(joinJson.toString())
                Log.d(TAG, "Conectado a relay WebSocket de TikTok")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val parsed = parseRelayMessage(text) ?: return
                    scope.launch { _messageFlow.emit(parsed) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parseando evento del relay TikTok: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Fallo del relay TikTok: ${t.message}")
                _connectionState.value = ConnectionState.Error(
                    t.localizedMessage ?: "Error de relay TikTok"
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }

        currentWebSocket = okHttpClient.newWebSocket(request, listener)
    }

    /** Acepta los formatos habituales de los relays (evento `chat` con `comment`). */
    internal fun parseRelayMessage(raw: String): ChatMessage? {
        val root = JSONObject(raw)
        val event = root.optString("event").ifBlank { root.optString("type") }
        if (event.isNotBlank() && !event.contains("chat", ignoreCase = true)) return null

        val payload = root.optJSONObject("data") ?: root

        val content = payload.optString("comment")
            .ifBlank { payload.optString("text") }
            .ifBlank { payload.optString("message") }
            .ifBlank { payload.optString("content") }
            .trim()
        if (content.isBlank()) return null

        return ChatMessage(
            platform = Platform.TIKTOK,
            username = resolveUsername(payload),
            message = content,
            timestamp = System.currentTimeMillis(),
            userColor = DEFAULT_TIKTOK_COLOR
        )
    }

    companion object {
        private const val TAG = "TikTokChatClient"
        private const val MAX_SEEN_KEYS = 600
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val STALE_TIMEOUT_MS = 60_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val MAX_RECONNECT_ATTEMPTS = 12
        private val DEFAULT_TIKTOK_COLOR = Color(0xFFFE2C55)
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
