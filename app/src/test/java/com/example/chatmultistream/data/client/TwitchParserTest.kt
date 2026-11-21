package com.example.chatmultistream.data.client

import androidx.compose.ui.graphics.Color
import com.example.chatmultistream.model.ChatEvent
import com.example.chatmultistream.model.ChatEventType
import com.example.chatmultistream.model.EmoteSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TwitchParserTest {

    private val client = TwitchChatClient()

    @Test
    fun parsea_privmsg_con_tags() {
        val line = "@badge-info=;color=#1E90FF;display-name=Neo;emotes=;id=abc;mod=0;" +
                "tmi-sent-ts=1;user-type= :neo!neo@neo.tmi.twitch.tv PRIVMSG #canal :hola mundo"

        val message = client.parseIrcPrivmsg(line)
        assertEquals("Neo", message?.username)
        assertEquals("hola mundo", message?.message)
        assertEquals(Color(0xFF1E90FF).value, message?.userColor?.value)
    }

    @Test
    fun parsea_privmsg_sin_tags() {
        val message = client.parseIrcPrivmsg(":neo!neo@neo.tmi.twitch.tv PRIVMSG #canal :sin tags")
        assertEquals("neo", message?.username)
        assertEquals("sin tags", message?.message)
        assertNull(message?.userColor)
    }

    @Test
    fun parsea_emotes_del_tag() {
        val line = "@display-name=Neo;color=;emotes=25:0-4,12-16/1902:6-10;id=abc;mod=0;" +
                "tmi-sent-ts=1;user-type= :neo!neo@neo.tmi.twitch.tv PRIVMSG #canal :Kappa Keepo Kappa"

        val message = client.parseIrcPrivmsg(line)
        assertEquals("Kappa Keepo Kappa", message?.message)
        assertEquals(
            listOf(
                EmoteSpan(0, 5, "Kappa", "https://static-cdn.jtvnw.net/emoticons/v2/25/static/dark/2.0"),
                EmoteSpan(6, 11, "Keepo", "https://static-cdn.jtvnw.net/emoticons/v2/1902/static/dark/2.0"),
                EmoteSpan(12, 17, "Kappa", "https://static-cdn.jtvnw.net/emoticons/v2/25/static/dark/2.0")
            ),
            message?.emotes
        )
    }

    @Test
    fun parsea_emotes_con_emoji_astral_antes() {
        val line = "@display-name=Neo;color=;emotes=25:2-6;id=abc;mod=0;tmi-sent-ts=1;user-type= " +
                ":neo!neo@neo.tmi.twitch.tv PRIVMSG #canal :\uD83D\uDE00 Kappa"

        val message = client.parseIrcPrivmsg(line)
        val span = message?.emotes?.single()
        assertEquals("Kappa", span?.name)
        assertEquals(3, span?.start)
        assertEquals(8, span?.end)
        assertEquals("Kappa", message?.message?.substring(span!!.start, span.end))
    }

    @Test
    fun parsea_emotes_de_suscripcion_con_id_emotesv2() {
        val emoteId = "emotesv2_9035170c8c7843a1bae4aa5f0b03190b"
        val line = "@display-name=Neo;color=;emotes=$emoteId:0-6;id=abc;mod=0;" +
                "tmi-sent-ts=1;user-type= :neo!neo@neo.tmi.twitch.tv PRIVMSG #canal :miEmote gg"

        val message = client.parseIrcPrivmsg(line)
        assertEquals("miEmote gg", message?.message)
        assertEquals(
            listOf(
                EmoteSpan(
                    start = 0,
                    end = 7,
                    name = "miEmote",
                    url = "https://static-cdn.jtvnw.net/emoticons/v2/$emoteId/static/dark/2.0"
                )
            ),
            message?.emotes
        )
    }

    @Test
    fun ignora_ids_de_emote_invalidos() {
        assertEquals(
            emptyList<EmoteSpan>(),
            client.parseEmoteSpans("bad-id:0-3", "abc")
        )
        assertEquals(
            emptyList<EmoteSpan>(),
            client.parseEmoteSpans(":0-3", "abc")
        )
    }

    @Test
    fun ignora_lineas_que_no_son_privmsg() {
        assertNull(client.parseIrcPrivmsg("PING :tmi.twitch.tv"))
        assertNull(client.parseIrcPrivmsg(":tmi.twitch.tv 001 justinfan123 :Welcome to the chat"))
        assertNull(client.parseIrcPrivmsg(""))
    }

    @Test
    fun parsea_suscripcion_nueva() {
        val line = "@display-name=Neo;login=neo;msg-id=sub;msg-param-cumulative-months=1;" +
                "msg-param-sub-plan=1000;color=#1E90FF :tmi.twitch.tv USERNOTICE #canal :"

        val message = client.parseUserNotice(line)
        assertEquals("Neo", message?.username)
        assertEquals("", message?.message)
        assertEquals(
            ChatEvent(type = ChatEventType.SUBSCRIPTION, count = 1),
            message?.event
        )
    }

    @Test
    fun parsea_resub_con_meses_y_mensaje() {
        val line = "@display-name=Neo;login=neo;msg-id=resub;msg-param-cumulative-months=6;" +
                "msg-param-sub-plan=2000;color=;emotes=; :tmi.twitch.tv USERNOTICE #canal :" +
                "seis meses ya"

        val message = client.parseUserNotice(line)
        assertEquals("seis meses ya", message?.message)
        assertEquals(
            ChatEvent(type = ChatEventType.SUBSCRIPTION, count = 6),
            message?.event
        )
    }

    @Test
    fun parsea_sub_regalada() {
        val line = "@display-name=Neo;login=neo;msg-id=subgift;msg-param-months=1;" +
                "msg-param-recipient-display-name=Ana;msg-param-sub-plan=1000;" +
                "msg-param-gift-months=1 :tmi.twitch.tv USERNOTICE #canal :"

        val message = client.parseUserNotice(line)
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
    fun parsea_mega_regalo_de_subs() {
        val line = "@display-name=Neo;login=neo;msg-id=submysterygift;" +
                "msg-param-mass-gift-count=10;msg-param-sub-plan=1000 " +
                ":tmi.twitch.tv USERNOTICE #canal"

        val message = client.parseUserNotice(line)
        assertEquals(
            ChatEvent(type = ChatEventType.GIFT_SUBSCRIPTION, count = 10),
            message?.event
        )
    }

    @Test
    fun parsea_raid_con_espectadores() {
        val line = "@display-name=Neo;login=neo;msg-id=raid;msg-param-viewerCount=250;" +
                "msg-param-displayName=Neo :tmi.twitch.tv USERNOTICE #canal :"

        val message = client.parseUserNotice(line)
        assertEquals(
            ChatEvent(type = ChatEventType.RAID, count = 250),
            message?.event
        )
    }

    @Test
    fun parsea_anuncio() {
        val line = "@display-name=Neo;login=neo;msg-id=announcement;msg-param-color=PRIMARY " +
                ":tmi.twitch.tv USERNOTICE #canal :hola a todos"

        val message = client.parseUserNotice(line)
        assertEquals("hola a todos", message?.message)
        assertEquals(ChatEvent(type = ChatEventType.ANNOUNCEMENT), message?.event)
    }

    @Test
    fun ignora_usernotice_desconocido() {
        assertNull(
            client.parseUserNotice(
                "@display-name=Neo;msg-id=bitsbadgetier :tmi.twitch.tv USERNOTICE #canal :"
            )
        )
        assertNull(client.parseUserNotice(":tmi.twitch.tv USERNOTICE #canal :"))
    }

    @Test
    fun parsea_cheer_y_quita_el_prefijo_del_texto() {
        val line = "@bits=100;display-name=Neo;color=;emotes=25:9-13;id=abc;" +
                "tmi-sent-ts=1 :neo!neo@neo.tmi.twitch.tv PRIVMSG #canal :Cheer100 Kappa"

        val message = client.parseIrcPrivmsg(line)
        assertEquals("Kappa", message?.message)
        assertEquals(ChatEvent(type = ChatEventType.CHEER, count = 100), message?.event)
        assertEquals(
            listOf(
                EmoteSpan(0, 5, "Kappa", "https://static-cdn.jtvnw.net/emoticons/v2/25/static/dark/2.0")
            ),
            message?.emotes
        )
    }

    @Test
    fun parsea_canje_de_recompensa_del_canal() {
        val line = "@custom-reward-id=reward-123;display-name=Neo;color=;emotes=;" +
                "id=abc;tmi-sent-ts=1 :neo!neo@neo.tmi.twitch.tv PRIVMSG #canal :" +
                "Canjeé una recompensa"

        val message = client.parseIrcPrivmsg(line)
        assertEquals("Canjeé una recompensa", message?.message)
        assertEquals(ChatEvent(type = ChatEventType.REDEMPTION), message?.event)
    }

    @Test
    fun parsea_mensaje_destacado() {
        val line = "@display-name=Neo;color=;emotes=;msg-id=highlighted-message;" +
                "id=abc :neo!neo@neo.tmi.twitch.tv PRIVMSG #canal :destacado"

        val message = client.parseIrcPrivmsg(line)
        assertEquals(ChatEvent(type = ChatEventType.HIGHLIGHT), message?.event)
    }

    @Test
    fun los_mensajes_normales_no_llevan_suceso() {
        val line = "@display-name=Neo;color=;emotes=;id=abc;" +
                "tmi-sent-ts=1 :neo!neo@neo.tmi.twitch.tv PRIVMSG #canal :hola"

        assertNull(client.parseIrcPrivmsg(line)?.event)
    }
}
