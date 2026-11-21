package com.example.chatmultistream.model

/**
 * Límite de mensajes que se publican en la interfaz por segundo.
 *
 * Las plataformas grandes aplican un "modo lento" parecido: durante una ráfaga
 * muy intensa no muestran todos los mensajes, sino una muestra. Aquí sirve para
 * dos cosas:
 *
 *  - Evitar que la lista reciba cientos de altas por segundo (menos trabajo de
 *    composición y de scroll).
 *  - Evitar que las animaciones y las tarjetas se solapen cuando el chat va
 *    demasiado rápido.
 *
 * Los mensajes que exceden el límite se descartan: no se acumulan para después,
 * porque un backlog crecería sin control y la vista se retrasaría del directo.
 */
enum class MessageRateLimit(val messagesPerSecond: Int) {
    UNLIMITED(0),
    HIGH(30),
    MEDIUM(15),
    LOW(8),
    SLOW(4),
    SLOWEST(2);

    val isLimited: Boolean get() = messagesPerSecond > 0

    companion object {
        val DEFAULT = UNLIMITED

        fun fromName(name: String?): MessageRateLimit =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Cantidad máxima de mensajes que se conservan en memoria y en la lista.
 * Al recortar se descartan siempre los más antiguos (los de arriba).
 */
enum class HistoryLimit(val messages: Int) {
    VERY_SHORT(50),
    SHORT(100),
    DEFAULT(500),
    LARGE(1_000),
    UNLIMITED(Int.MAX_VALUE);

    companion object {
        fun fromName(name: String?): HistoryLimit =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
