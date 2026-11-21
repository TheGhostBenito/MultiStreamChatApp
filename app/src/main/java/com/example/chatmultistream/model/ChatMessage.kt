package com.example.chatmultistream.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Posición de un emote dentro de [ChatMessage.message].
 *
 * [start] y [end] son índices de caracteres de Kotlin (UTF-16) sobre el texto final.
 * Si la carga de imágenes está desactivada, el texto original (el nombre del emote)
 * sigue siendo legible.
 *
 * [Immutable] permite a Compose saltar la recomposición de los mensajes que no
 * cambian mientras llegan mensajes nuevos; es clave para que el scroll no vibre.
 */
@Immutable
data class EmoteSpan(
    val start: Int,
    val end: Int,
    val name: String,
    val url: String
)

/**
 * Tipo de suceso que se muestra como una tarjeta destacada en lugar de un
 * mensaje normal: suscripciones, regalos, donaciones, canjes, etc.
 *
 * Cada valor existe porque la interfaz le da un color, un icono y una etiqueta
 * propios; los tipos no se agrupan por plataforma para que el usuario
 * reconozca el suceso aunque venga de Twitch, YouTube, Kick o TikTok.
 */
@Immutable
enum class ChatEventType {
    /** Suscripción de Twitch con sus meses acumulados. */
    SUBSCRIPTION,
    /** Suscripción regalada por otro usuario (Twitch, Kick o YouTube). */
    GIFT_SUBSCRIPTION,
    /** Bits de Twitch (cheer). */
    CHEER,
    /** Donación de YouTube (Super Chat) o equivalente de otra plataforma. */
    DONATION,
    /** Super Sticker de YouTube. */
    SUPER_STICKER,
    /** Membresía de YouTube. */
    MEMBERSHIP,
    /** Membresías regaladas de YouTube, una o varias. */
    GIFT_MEMBERSHIP,
    /** Raid de Twitch: llegada de espectadores desde otro canal. */
    RAID,
    /** Canje de una recompensa con puntos del canal (Twitch). */
    REDEMPTION,
    /** Mensaje destacado por un moderador o por el propio canal. */
    HIGHLIGHT,
    /** Anuncio del canal. */
    ANNOUNCEMENT,
    /** Regalo de stream (TikTok: rosas, galaxias…). */
    GIFT
}

/**
 * Datos estructurados de un suceso, sin texto ya formateado por la plataforma.
 *
 * La interfaz compone la etiqueta con recursos localizados y elige el color
 * según [type]. Guardar el texto ya formateado rompería dos cosas: el cambio
 * de idioma no afectaría a los avisos y habría que analizar la frase para
 * extraer cantidades o importes.
 *
 * @property type qué ocurrió; decide el color y el icono de la tarjeta.
 * @property count número de meses, bits, subs regaladas, regalos o
 *   espectadores de raid. `0` significa que el tipo no usa cantidad.
 * @property amountText importe tal cual lo entrega la plataforma (por ejemplo
 *   `$5.00`). Es un dato monetario, no una frase traducible.
 * @property recipient usuario que recibe una sub regalada, si se conoce.
 * @property giftName nombre del regalo de stream (TikTok: "Rosa", "Galaxia"…).
 *   Lo entrega la plataforma y no se traduce.
 */
@Immutable
data class ChatEvent(
    val type: ChatEventType,
    val count: Int = 0,
    val amountText: String? = null,
    val recipient: String? = null,
    val giftName: String? = null
)

@Immutable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    /** Plataforma de origen: decide el chip y el color base del mensaje. */
    val platform: Platform,
    /** Nombre visible: nickname de la plataforma o alias del usuario. */
    val username: String,
    /** Texto del chat; puede ir vacío en sucesos que solo muestran tarjeta. */
    val message: String,
    /** Hora del mensaje según la plataforma, normalizada a milisegundos. */
    val timestamp: Long = System.currentTimeMillis(),
    /**
     * Momento en que la app recibió el mensaje. Muchas plataformas solo dan
     * precisión de segundos en su marca de tiempo; usar la hora local evita
     * que ráfagas del mismo segundo se traten como "mensajes aislados" y
     * disparen animaciones superpuestas.
     */
    val receivedAt: Long = System.currentTimeMillis(),
    /** Insignias de rol o suscripción que ofrece la plataforma (mod, VIP…). */
    val badges: List<String> = emptyList(),
    /** Color elegido por el usuario; `null` usa el primario del tema. */
    val userColor: Color? = null,
    val emotes: List<EmoteSpan> = emptyList(),
    /**
     * Si no es null, el mensaje se pinta como suceso destacado. El texto de
     * [message] sigue mostrándose debajo cuando existe (por ejemplo, el
     * comentario de un Super Chat).
     */
    val event: ChatEvent? = null
)
