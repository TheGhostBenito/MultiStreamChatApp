package com.example.chatmultistream.data.client

import com.example.chatmultistream.model.ChatMessage
import com.example.chatmultistream.model.ConnectionState
import com.example.chatmultistream.model.Platform
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ChatClient {
    val platform: Platform
    val connectionState: StateFlow<ConnectionState>
    val messageFlow: SharedFlow<ChatMessage>

    suspend fun connect(channelName: String, authToken: String? = null)
    suspend fun disconnect()

    /**
     * Libera todos los recursos del cliente (sockets, jobs y coroutines).
     * Debe ser idempotente y no bloqueante.
     */
    fun close()
}
