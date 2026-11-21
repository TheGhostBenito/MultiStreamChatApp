package com.example.chatmultistream.model

/**
 * Limitador de caudal ("modo lento") con ventana fija de un segundo.
 *
 * Se usa desde el hilo de batching del repositorio y desde el hilo principal
 * (mensajes inyectados), por eso todos los métodos están sincronizados.
 *
 * No acumula deuda: si llegan más mensajes de los permitidos, el sobrante se
 * descarta. Acumularlos haría que la vista se retrasara cada vez más del
 * directo y consumiría memoria sin límite.
 *
 * [clockMs] se inyecta para poder probar la ventana sin esperas reales.
 */
class MessageRateGate(
    initialLimit: Int = 0,
    private val windowMs: Long = 1_000L,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 }
) {

    private var limit: Int = initialLimit.coerceAtLeast(0)
    private var windowStartMs: Long = 0L
    private var usedInWindow: Int = 0

    @Synchronized
    fun setLimit(messagesPerSecond: Int) {
        limit = messagesPerSecond.coerceAtLeast(0)
        reset()
    }

    /**
     * Indica cuántos de los [count] mensajes que llegan ahora se pueden publicar.
     * Devuelve [count] cuando no hay límite.
     */
    @Synchronized
    fun available(count: Int): Int {
        if (count <= 0) return 0
        if (limit <= 0) return count

        val now = clockMs()
        if (now - windowStartMs >= windowMs) {
            windowStartMs = now
            usedInWindow = 0
        }

        val room = (limit - usedInWindow).coerceAtLeast(0)
        val allowed = minOf(room, count)
        usedInWindow += allowed
        return allowed
    }

    @Synchronized
    fun reset() {
        windowStartMs = 0L
        usedInWindow = 0
    }
}
