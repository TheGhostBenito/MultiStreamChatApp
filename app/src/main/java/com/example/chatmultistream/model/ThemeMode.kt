package com.example.chatmultistream.model

/**
 * Tema visual elegido por el usuario en los ajustes.
 *
 * [SYSTEM] mantiene el comportamiento actual: sigue el modo claro/oscuro del
 * dispositivo. Los nombres visibles se resuelven en la interfaz con recursos
 * para poder traducirlos.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
