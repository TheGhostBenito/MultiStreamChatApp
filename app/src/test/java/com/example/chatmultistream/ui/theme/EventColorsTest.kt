package com.example.chatmultistream.ui.theme

import com.example.chatmultistream.model.ChatEventType
import com.example.chatmultistream.model.Platform
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Comprueba la separación visual entre mensajes normales, canjes y dinero.
 *
 * Son pruebas de regresión: los colores pueden ajustarse de tono, pero un
 * canje nunca debe volver al morado de Twitch ni al dorado de las donaciones.
 * Se recorren tema claro y oscuro porque cada uno tiene su propia variante.
 */
class EventColorsTest {

    @Test
    fun el_canje_no_usa_el_color_de_un_mensaje_normal() {
        // Un mensaje normal se identifica por el color de marca de su
        // plataforma; el canje debe tener un acento propio en todas ellas.
        for (darkTheme in listOf(true, false)) {
            for (platform in Platform.entries) {
                val redemption = eventAccentColor(
                    ChatEventType.REDEMPTION,
                    platform,
                    darkTheme
                )
                assertNotEquals(
                    "El canje de $platform (dark=$darkTheme) usa el color de mensaje normal",
                    platform.brandColor,
                    redemption
                )
            }
        }
    }

    @Test
    fun el_canje_no_usa_el_color_de_las_donaciones() {
        // Canje y dinero son sucesos distintos: si compartieran color habría
        // que leer la etiqueta para diferenciarlos.
        for (darkTheme in listOf(true, false)) {
            val redemption = eventAccentColor(
                ChatEventType.REDEMPTION,
                Platform.TWITCH,
                darkTheme
            )
            val donation = eventAccentColor(
                ChatEventType.DONATION,
                Platform.TWITCH,
                darkTheme
            )
            val sticker = eventAccentColor(
                ChatEventType.SUPER_STICKER,
                Platform.TWITCH,
                darkTheme
            )

            assertNotEquals(donation, redemption)
            assertNotEquals(sticker, redemption)
        }
    }

    @Test
    fun las_donaciones_tampoco_usan_el_color_de_mensaje_normal() {
        // El dorado debe destacar sobre cualquier plataforma, incluida
        // YouTube, cuyo rojo de marca no se usa para sucesos.
        for (darkTheme in listOf(true, false)) {
            for (platform in Platform.entries) {
                assertNotEquals(
                    platform.brandColor,
                    eventAccentColor(ChatEventType.DONATION, platform, darkTheme)
                )
            }
        }
    }
}
