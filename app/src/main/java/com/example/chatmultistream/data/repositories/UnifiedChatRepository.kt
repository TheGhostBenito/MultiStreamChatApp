package com.example.chatmultistream.data.repositories

import com.example.chatmultistream.data.client.ChatClient
import com.example.chatmultistream.data.client.KickChatClient
import com.example.chatmultistream.data.client.TikTokChatClient
import com.example.chatmultistream.data.client.TwitchChatClient
import com.example.chatmultistream.data.client.YouTubeChatClient
import com.example.chatmultistream.model.ChatEvent
import com.example.chatmultistream.model.ChatMessage
import com.example.chatmultistream.model.ConnectionState
import com.example.chatmultistream.model.HistoryLimit
import com.example.chatmultistream.model.MessageRateGate
import com.example.chatmultistream.model.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UnifiedChatRepository(
    private val twitchClient: ChatClient = TwitchChatClient(),
    private val youtubeClient: ChatClient = YouTubeChatClient(),
    private val kickClient: ChatClient = KickChatClient(),
    private val tiktokClient: ChatClient = TikTokChatClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    initialHistoryLimit: Int = HistoryLimit.DEFAULT.messages,
    initialMessageRateLimit: Int = 0
) {

    /**
     * Se lee desde el hilo de batching y desde el hilo principal
     * (`injectMessage`), por eso es volátil.
     */
    @Volatile
    private var historyLimit: Int = initialHistoryLimit.coerceAtLeast(1)

    /** "Modo lento": ventana de 1 s compartida por todas las plataformas. */
    private val rateGate = MessageRateGate(initialMessageRateLimit)

    private val clients: Map<Platform, ChatClient> = mapOf(
        Platform.TWITCH to twitchClient,
        Platform.YOUTUBE to youtubeClient,
        Platform.KICK to kickClient,
        Platform.TIKTOK to tiktokClient
    )

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val incomingMessages = Channel<ChatMessage>(Channel.UNLIMITED)

    val connectionStates: StateFlow<Map<Platform, ConnectionState>> = combine(
        twitchClient.connectionState,
        youtubeClient.connectionState,
        kickClient.connectionState,
        tiktokClient.connectionState
    ) { twitch, youtube, kick, tiktok ->
        mapOf(
            Platform.TWITCH to twitch,
            Platform.YOUTUBE to youtube,
            Platform.KICK to kick,
            Platform.TIKTOK to tiktok
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = Platform.entries.associateWith { ConnectionState.Disconnected }
    )

    init {
        // Unify incoming messages from all platforms into a single stream
        scope.launch {
            merge(
                twitchClient.messageFlow,
                youtubeClient.messageFlow,
                kickClient.messageFlow,
                tiktokClient.messageFlow
            ).collect { newMessage ->
                incomingMessages.send(newMessage)
            }
        }

        // Agrupa ráfagas de mensajes en una sola actualización de estado.
        // Sin esto, un chat rápido provoca una recomposición (y un auto-scroll)
        // por cada mensaje, lo que se percibe como tirones al hacer scroll.
        scope.launch {
            val batch = ArrayList<ChatMessage>(BATCH_MAX_SIZE)
            while (isActive) {
                val first = incomingMessages.receiveCatching().getOrNull() ?: break
                batch.add(first)
                delay(BATCH_WINDOW_MS)
                while (batch.size < BATCH_MAX_SIZE) {
                    val next = incomingMessages.tryReceive().getOrNull() ?: break
                    batch.add(next)
                }
                appendMessages(batch)
                batch.clear()
            }
        }
    }

    private fun appendMessages(batch: List<ChatMessage>) {
        if (batch.isEmpty()) return
        val allowed = applyRateLimit(batch)
        if (allowed.isEmpty()) return
        _chatMessages.update { current ->
            (current + allowed).takeLast(historyLimit)
        }
    }

    /** Aplica el "modo lento": deja pasar como máximo N mensajes por segundo. */
    private fun applyRateLimit(batch: List<ChatMessage>): List<ChatMessage> {
        if (batch.isEmpty()) return batch

        // Los sucesos (subs, regalos, donaciones, canjes) no cuentan para el
        // límite ni se descartan: son avisos puntuales que el usuario no debe
        // perderse aunque el chat esté saturado.
        val regularCount = batch.count { it.event == null }
        if (regularCount == 0) return batch

        val allowedRegular = rateGate.available(regularCount)
        if (allowedRegular >= regularCount) return batch

        var remainingRegular = allowedRegular
        return batch.filter { message ->
            if (message.event != null) {
                true
            } else if (remainingRegular > 0) {
                remainingRegular--
                true
            } else {
                false
            }
        }
    }

    /**
     * Cambia cuántos mensajes se conservan. Si el nuevo límite es menor que el
     * historial actual, se recortan inmediatamente los más antiguos para liberar
     * memoria.
     */
    fun setHistoryLimit(limit: Int) {
        val newLimit = limit.coerceAtLeast(1)
        historyLimit = newLimit
        _chatMessages.update { current ->
            if (current.size > newLimit) current.takeLast(newLimit) else current
        }
    }

    /**
     * Cambia el número máximo de mensajes publicados por segundo.
     * `0` desactiva el límite.
     */
    fun setMessageRateLimit(messagesPerSecond: Int) {
        rateGate.setLimit(messagesPerSecond)
    }

    suspend fun connect(platform: Platform, channelName: String, authToken: String? = null) {
        clients[platform]?.connect(channelName, authToken)
    }

    suspend fun disconnect(platform: Platform) {
        clients[platform]?.disconnect()
    }

    fun clearMessages() {
        _chatMessages.value = emptyList()
    }

    /** Libera sockets, jobs y coroutines de todos los clientes. */
    fun close() {
        clients.values.forEach { client ->
            runCatching { client.close() }
        }
        incomingMessages.close()
        _chatMessages.value = emptyList()
        scope.cancel()
    }

    /**
     * Inyecta un mensaje de prueba desde la barra de simulación.
     *
     * No pasa por los clientes ni por el "modo lento": es una acción manual
     * del usuario y debe verse al instante aunque el chat real ya haya
     * consumido el cupo del segundo. [event] permite probar las tarjetas
     * destacadas con los mismos tipos que llegan de las plataformas.
     *
     * Se respeta igualmente [historyLimit] para que la simulación no pueda
     * saltarse el recorte de historial configurado.
     */
    fun injectMessage(
        platform: Platform,
        user: String,
        text: String,
        event: ChatEvent? = null
    ) {
        val msg = ChatMessage(
            platform = platform,
            username = user,
            message = text,
            timestamp = System.currentTimeMillis(),
            event = event
        )
        _chatMessages.update { current ->
            (current + msg).takeLast(historyLimit)
        }
    }

    private companion object {
        /** Ventana para agrupar mensajes antes de publicarlos en el StateFlow. */
        const val BATCH_WINDOW_MS = 90L
        const val BATCH_MAX_SIZE = 200
    }
}
