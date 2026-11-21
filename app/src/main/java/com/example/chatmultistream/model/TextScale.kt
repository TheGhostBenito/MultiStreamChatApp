package com.example.chatmultistream.model

/**
 * Límites del ajuste de tamaño de texto de la app.
 *
 * Se guarda como porcentaje entero (sobre el tamaño de fuente del sistema)
 * para evitar errores de redondeo al sumar pasos de coma flotante.
 */
object TextScale {
    const val MIN_PERCENT = 80
    const val DEFAULT_PERCENT = 100
    const val MAX_PERCENT = 150
    const val STEP_PERCENT = 10
}
