package com.example.chatmultistream.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.chatmultistream.model.ChatEventType
import com.example.chatmultistream.model.Platform

/**
 * Color de acento de las tarjetas de suceso destacado.
 *
 * Un mensaje normal se identifica por el color de su plataforma (borde suave y
 * distintivo). Los sucesos usan colores propios para que se reconozcan de un
 * vistazo, sin depender de leer la etiqueta:
 *
 *  - Azul: suscripciones y membresías.
 *  - Rosa: regalos (subs o membresías regaladas).
 *  - Dorado: dinero (Super Chat, Super Sticker y regalos de stream).
 *  - Violeta: bits.
 *  - Turquesa: canjes con puntos del canal. Se eligió a propósito lejos del
 *    morado de Twitch (mensaje normal) y del dorado de las donaciones.
 *  - Naranja: raids.
 *
 * Los avisos y mensajes destacados conservan el color de su plataforma porque
 * no representan dinero ni recompensas.
 *
 * Cada tono tiene una variante oscura (brillante, legible sobre el fondo
 * oscuro) y una clara (oscura, legible sobre el fondo claro); el tinte de la
 * tarjeta reutiliza el mismo color con alfa bajo.
 *
 * @param platform solo se usa para los avisos (destacado/anuncio); el resto
 *   de tipos tiene un color independiente de la plataforma para que el mismo
 *   suceso se reconozca igual en Twitch, YouTube, Kick o TikTok.
 */
fun eventAccentColor(
    type: ChatEventType,
    platform: Platform,
    darkTheme: Boolean
): Color = when (type) {
    ChatEventType.SUBSCRIPTION,
    ChatEventType.MEMBERSHIP ->
        if (darkTheme) Color(0xFF8AB4F8) else Color(0xFF1A56C4)

    // Regalos: siempre algo que se obsequia, aunque el receptor cambie.
    ChatEventType.GIFT_SUBSCRIPTION,
    ChatEventType.GIFT_MEMBERSHIP ->
        if (darkTheme) Color(0xFFF48FB1) else Color(0xFFAD1457)

    // Los regalos de stream se pagan con monedas o diamantes, así que
    // comparten el dorado del dinero; el icono y la etiqueta los distinguen
    // de un Super Chat.
    ChatEventType.DONATION,
    ChatEventType.SUPER_STICKER,
    ChatEventType.GIFT ->
        if (darkTheme) Color(0xFFFFCA28) else Color(0xFF8A6100)

    ChatEventType.CHEER ->
        if (darkTheme) Color(0xFFC9A7FF) else Color(0xFF5B21B6)

    ChatEventType.REDEMPTION ->
        if (darkTheme) Color(0xFF4FD1C5) else Color(0xFF00695C)

    ChatEventType.RAID ->
        if (darkTheme) Color(0xFFFF9E6D) else Color(0xFFA33D00)

    // No son dinero ni recompensas: usar el color de la plataforma mantiene
    // la identidad del canal y evita inventar un color para cada aviso.
    ChatEventType.HIGHLIGHT,
    ChatEventType.ANNOUNCEMENT ->
        platform.highContrastTextColor(darkTheme)
}
