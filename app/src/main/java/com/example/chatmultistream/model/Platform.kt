package com.example.chatmultistream.model

import androidx.compose.ui.graphics.Color

enum class Platform(
    val displayName: String,
    val brandColor: Color,
    val onBrandColor: Color,
    /** Variante del color de marca legible sobre un fondo oscuro. */
    val highContrastColorOnDark: Color,
    /** Variante del color de marca legible sobre un fondo claro. */
    val highContrastColorOnLight: Color
) {
    TWITCH("Twitch", Color(0xFF9146FF), Color.White, Color(0xFFD6BBFF), Color(0xFF5B21B6)),
    YOUTUBE("YouTube", Color(0xFFFF0000), Color.White, Color(0xFFFF8A80), Color(0xFFB3261E)),
    KICK("Kick", Color(0xFF53FC18), Color.Black, Color(0xFFB6F59B), Color(0xFF1F6B00)),
    TIKTOK("TikTok", Color(0xFFFE2C55), Color.White, Color(0xFFFFB3C1), Color(0xFFB3003C));

    /**
     * Color del nombre de usuario que identifica a la plataforma y mantiene un
     * contraste alto sobre el fondo del tema activo.
     *
     * [brandColor] no sirve tal cual en todos los casos: por ejemplo el verde
     * de Kick sería ilegible sobre un fondo claro.
     */
    fun highContrastTextColor(darkTheme: Boolean): Color =
        if (darkTheme) highContrastColorOnDark else highContrastColorOnLight
}
