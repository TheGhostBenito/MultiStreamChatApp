package com.example.chatmultistream.data.client

import androidx.compose.ui.graphics.Color
import com.example.chatmultistream.model.ChatEvent
import com.example.chatmultistream.model.ChatEventType
import com.example.chatmultistream.model.EmoteSpan
import com.example.chatmultistream.model.Platform
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KickParserTest {

    private val client = KickChatClient()

    @Test
    fun sanitizeInput_acepta_slug_almohadilla_y_url() {
        assertEquals("westcol", client.sanitizeInput("  #WestCol "))
        assertEquals("westcol", client.sanitizeInput("https://kick.com/westcol"))
        assertEquals("westcol", client.sanitizeInput("https://kick.com/westcol/"))
        assertEquals("875396", client.sanitizeInput("875396"))
    }

    @Test
    fun cleanContent_traduce_emotes_y_recorta_espacios() {
        assertEquals(
            ":collectiblesHeels: hola",
            client.cleanContent("[emote:6406279:collectiblesHeels] hola")
        )
        assertEquals(
            ":xd: :pog: gg",
            client.cleanContent("[emote:123:xd] [emote:456:pog] gg")
        )
        assertEquals("texto normal", client.cleanContent("  texto normal  "))
    }

    @Test
    fun parseContent_genera_rangos_y_urls_de_emotes() {
        val parsed = client.parseContent("[emote:6406279:collectiblesHeels] hola")

        assertEquals(":collectiblesHeels: hola", parsed.text)
        assertEquals(1, parsed.emotes.size)

        val span = parsed.emotes.first()
        assertEquals(
            EmoteSpan(
                start = 0,
                end = ":collectiblesHeels:".length,
                name = "collectiblesHeels",
                url = "https://files.kick.com/emotes/6406279/fullsize"
            ),
            span
        )
        assertEquals(":collectiblesHeels:", parsed.text.substring(span.start, span.end))
    }

    @Test
    fun parseContent_ajusta_rangos_al_recortar_espacios() {
        val parsed = client.parseContent("  hola [emote:123:xd]  ")

        assertEquals("hola :xd:", parsed.text)
        val span = parsed.emotes.single()
        assertEquals(5, span.start)
        assertEquals(9, span.end)
        assertEquals(":xd:", parsed.text.substring(span.start, span.end))
    }

    @Test
    fun parseIsoTimestamp_usa_utc() {
        assertEquals(1794549465000L, client.parseIsoTimestamp("2026-11-13T05:57:45Z"))
    }

    @Test
    fun parseIsoTimestamp_acepta_offset_con_dos_puntos() {
        // Formato que usa el realtime (`created_at`), no el del historial.
        assertEquals(1794768655000L, client.parseIsoTimestamp("2026-11-15T18:50:55+00:00"))
    }

    @Test
    fun parseIsoTimestamp_cae_en_ahora_si_es_invalido() {
        val before = System.currentTimeMillis()
        val parsed = client.parseIsoTimestamp("no-es-fecha")
        val after = System.currentTimeMillis()
        assertTrue(parsed in before..after)
    }

    @Test
    fun parseIdentityColor_lee_hex_y_descarta_invalidos() {
        val color = client.parseIdentityColor(JSONObject("""{"color":"#E9113C"}"""))
        assertNotNull(color)
        assertEquals(Color(0xFFE9113C).value, color!!.value)

        assertNull(client.parseIdentityColor(JSONObject("""{"color":"red"}""")))
        assertNull(client.parseIdentityColor(JSONObject("""{}""")))
        assertNull(client.parseIdentityColor(null))
    }

    @Test
    fun parsea_suscripcion_del_realtime() {
        val message = client.parseSubscriptionEvent(
            JSONObject("""{"chatroom_id":123,"username":"Ana","months":6}""")
        )

        assertEquals(Platform.KICK, message?.platform)
        assertEquals("Ana", message?.username)
        assertEquals("", message?.message)
        assertEquals(
            ChatEvent(type = ChatEventType.SUBSCRIPTION, count = 6),
            message?.event
        )
    }

    @Test
    fun parsea_suscripcion_sin_meses() {
        val message = client.parseSubscriptionEvent(
            JSONObject("""{"chatroom_id":123,"username":"Ana"}""")
        )
        assertEquals(
            ChatEvent(type = ChatEventType.SUBSCRIPTION, count = 1),
            message?.event
        )
    }

    @Test
    fun parsea_regalo_de_subs_del_realtime() {
        val message = client.parseGiftedSubscriptionsEvent(
            JSONObject(
                """
                {
                  "chatroom_id": 123,
                  "gifter_username": "Pedro",
                  "gifted_usernames": ["Ana", "Luis", "Marta"]
                }
                """.trimIndent()
            )
        )

        assertEquals("Pedro", message?.username)
        assertEquals(
            ChatEvent(type = ChatEventType.GIFT_SUBSCRIPTION, count = 3),
            message?.event
        )
    }

    @Test
    fun parsea_regalo_de_una_sub_con_destinatario() {
        val message = client.parseGiftedSubscriptionsEvent(
            JSONObject(
                """
                {
                  "chatroom_id": 123,
                  "gifter_username": "Pedro",
                  "gifted_usernames": ["Ana"]
                }
                """.trimIndent()
            )
        )

        assertEquals(
            ChatEvent(
                type = ChatEventType.GIFT_SUBSCRIPTION,
                count = 1,
                recipient = "Ana"
            ),
            message?.event
        )
    }

    @Test
    fun ignora_sucesos_sin_usuario() {
        assertNull(client.parseSubscriptionEvent(JSONObject("""{"months":3}""")))
        assertNull(
            client.parseGiftedSubscriptionsEvent(
                JSONObject("""{"gifted_usernames":["Ana"]}""")
            )
        )
    }
}
