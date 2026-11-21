package com.example.chatmultistream.data.client

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class TikTokParserTest {

    private val client = TikTokChatClient()

    @Test
    fun sanitizeInput_acepta_arroba_y_url() {
        assertEquals("streamer", client.sanitizeInput("@streamer"))
        assertEquals("streamer", client.sanitizeInput("https://www.tiktok.com/@streamer"))
        assertEquals("streamer", client.sanitizeInput("https://www.tiktok.com/@streamer/"))
        assertEquals("streamer", client.sanitizeInput("  streamer  "))
    }

    @Test
    fun extractTtwid_encuentra_la_cookie_entre_varias() {
        val headers = listOf(
            "tt_csrf_token=abc; path=/",
            "ttwid=1%7Cabc%7C1794550278%7Cdef; Domain=.tiktok.com; Path=/; HttpOnly"
        )
        assertEquals("1%7Cabc%7C1794550278%7Cdef", client.extractTtwid(headers))
    }

    @Test
    fun extractTtwid_devuelve_null_si_no_existe() {
        assertNull(client.extractTtwid(listOf("foo=bar; path=/")))
        assertNull(client.extractTtwid(emptyList()))
    }

    @Test
    fun parseEntry_lee_formato_webcast_con_payload() {
        val entry = JSONObject(
            """
            {
              "method": "WebcastChatMessage",
              "payload": {
                "id": "m-1",
                "content": "hola chat",
                "user": { "nickname": "Ana" },
                "createTime": 1700000000000
              }
            }
            """.trimIndent()
        )

        val parsed = client.parseEntry(entry)
        assertTrue(parsed != null)
        assertEquals("m-1", parsed!!.dedupeKey)
        assertEquals("hola chat", parsed.message.message)
        assertEquals("Ana", parsed.message.username)
        assertEquals(1700000000000L, parsed.message.timestamp)
    }

    @Test
    fun parseEntry_lee_formato_plano_sin_payload() {
        val entry = JSONObject("""{"content":"hey","user":"Bob"}""")

        val parsed = client.parseEntry(entry)
        assertTrue(parsed != null)
        assertEquals("hey", parsed!!.message.message)
        assertEquals("Bob", parsed.message.username)
        assertTrue(parsed.dedupeKey!!.startsWith("Bob|"))
    }

    @Test
    fun parseEntry_ignora_eventos_que_no_son_chat() {
        assertNull(
            client.parseEntry(
                JSONObject("""{"method":"WebcastGiftMessage","payload":{"content":"regalo"}}""")
            )
        )
        assertNull(
            client.parseEntry(
                JSONObject("""{"method":"WebcastChatMessage","payload":{"content":"   "}}""")
            )
        )
    }

    @Test
    fun resolveTimestamp_normaliza_segundos_milisegundos_y_microsegundos() {
        assertEquals(1700000000000L, client.resolveTimestamp(JSONObject().put("createTime", 1700000000L)))
        assertEquals(1700000000000L, client.resolveTimestamp(JSONObject().put("createTime", 1700000000000L)))
        assertEquals(1700000000000L, client.resolveTimestamp(JSONObject().put("createTime", 1700000000000000L)))
    }

    @Test
    fun parseRelayMessage_lee_evento_chat() {
        val relay = JSONObject(
            """{"event":"chat","data":{"comment":"hola relay","uniqueId":"bob"}}"""
        )

        val message = client.parseRelayMessage(relay.toString())
        assertTrue(message != null)
        assertEquals("hola relay", message!!.message)
        assertEquals("bob", message.username)

        assertNull(client.parseRelayMessage("""{"event":"gift","data":{"comment":"regalo"}}"""))
    }

    // ------------------------------------------------- protocolo WebSocket real

    private fun fixture(): ByteArray = Base64.getDecoder().decode(
        javaClass.classLoader!!
            .getResourceAsStream("tiktok_chat_fixture.b64")!!
            .bufferedReader()
            .use { it.readText() }
            .trim()
    )

    @Test
    fun decodeChat_lee_un_payload_real_de_tiktok() {
        val chat = WebcastProto.decodeChat(fixture())

        assertNotNull(chat)
        assertEquals("🔥🔥🔥", chat!!.nickname)
        assertEquals("user3442487780410", chat.uniqueId)
        assertEquals("🔞🍆", chat.content)
        assertEquals(1794553300071L, chat.createTime)
        assertEquals(7707547666575067926L, chat.msgId)
    }

    @Test
    fun decodeChat_descomprime_gzip_antes_de_parsear() {
        val gzipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(fixture()) }
        }.toByteArray()

        val restored = WebcastProto.gunzipIfNeeded(gzipped)
        assertEquals(fixture().size, restored.size)
        assertNotNull(WebcastProto.decodeChat(restored))
    }

    @Test
    fun decodeResponse_encuentra_el_mensaje_de_chat() {
        val msgId = 7707547666575067926L
        val responseMessage = proto {
            string(1, "WebcastChatMessage")
            bytes(2, fixture())
            varint(3, msgId)
        }
        val response = proto {
            bytes(1, responseMessage)
            bytes(5, byteArrayOf(7, 7))
            varint(9, 1L)
        }

        val decoded = WebcastProto.decodeResponse(response)
        assertNotNull(decoded)
        assertEquals(1, decoded!!.messages.size)
        assertEquals("WebcastChatMessage", decoded.messages[0].method)
        assertEquals(msgId, decoded.messages[0].msgId)
        assertTrue(decoded.needsAck)
        assertArrayEquals(byteArrayOf(7, 7), decoded.internalExt)
    }

    @Test
    fun decodeResponse_acepta_payload_gzip() {
        val responseMessage = proto {
            string(1, "WebcastChatMessage")
            bytes(2, fixture())
        }
        val response = proto { bytes(1, responseMessage) }
        val gzipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(response) }
        }.toByteArray()

        val decoded = WebcastProto.decodeResponse(gzipped)
        assertEquals(1, decoded?.messages?.size)
    }

    @Test
    fun buildHeartbeat_genera_una_trama_hb() {
        val frame = WebcastProto.decodeFrame(WebcastProto.buildHeartbeat("7706695538414062349"))
        assertEquals("hb", frame?.payloadType)
        assertTrue(frame!!.payload.isNotEmpty())
    }

    @Test
    fun buildEnterRoom_envia_identidad_audience() {
        val frame = WebcastProto.decodeFrame(WebcastProto.buildEnterRoom("7706695538414062349"))
        assertEquals("im_enter_room", frame?.payloadType)
        assertTrue(String(frame!!.payload, Charsets.UTF_8).contains("audience"))
    }

    @Test
    fun buildAck_conserva_log_id_y_payload() {
        val internalExt = byteArrayOf(1, 2, 3)
        val frame = WebcastProto.decodeFrame(WebcastProto.buildAck(42L, internalExt))
        assertEquals("ack", frame?.payloadType)
        assertEquals(42L, frame?.logId)
        assertArrayEquals(internalExt, frame?.payload)
    }

    @Test
    fun decodeGift_lee_nombre_combo_y_autor() {
        // Protobuf antepone la longitud a cada sub-mensaje, así que `Common`,
        // `User` y `Gift` se construyen por separado y se anidan igual que en
        // una trama real. Los números de campo coinciden con el esquema de
        // `WebcastGiftMessage` que usa WebcastProto.
        val common = proto {
            varint(2, 12345L)                 // msgId
            varint(4, 1794553300000L)         // createTime en milisegundos
        }
        val user = proto {
            string(3, "TokLover")             // nickname
            string(38, "toklover")            // uniqueId
        }
        val giftInfo = proto {
            string(16, "Rosa")                // nombre visible
            varint(12, 1L)                    // diamondCount
            varint(10, 1L)                    // combo = true
        }
        val giftMessage = proto {
            bytes(1, common)
            varint(2, 5655L)                  // giftId
            varint(5, 5L)                     // repeatCount
            varint(6, 5L)                     // comboCount
            bytes(7, user)
            varint(9, 1L)                     // repeatEnd: cierre del combo
            bytes(15, giftInfo)
        }

        val gift = WebcastProto.decodeGift(giftMessage)
        assertNotNull(gift)
        assertEquals("Rosa", gift!!.giftName)
        assertEquals("TokLover", gift.nickname)
        assertEquals("toklover", gift.uniqueId)
        assertEquals(5, gift.comboCount)
        assertEquals(5, gift.repeatCount)
        assertEquals(1, gift.repeatEnd)
        assertEquals(1, gift.diamondCount)
        assertTrue(gift.combo)
        assertEquals(12345L, gift.msgId)
        assertEquals(1794553300000L, gift.createTime)
    }

    @Test
    fun decodeGift_sin_nombre_se_descarta() {
        // Sin nombre no hay nada legible que pintar; además, descartar estas
        // cargas evita que métodos parecidos acaben como tarjetas vacías.
        val giftMessage = proto {
            varint(2, 5655L)
            varint(5, 1L)
        }

        assertNull(WebcastProto.decodeGift(giftMessage))
    }

    /**
     * Reproduce los tres casos que llegan del servidor: un paso intermedio de
     * combo, el cierre del combo y un regalo que no admite combo. El paso
     * intermedio debe descartarse para no llenar el chat de tarjetas
     * "×1, ×2, ×3…".
     */
    @Test
    fun shouldEmitGift_solo_publica_el_final_del_combo() {
        fun sample(combo: Boolean, repeatEnd: Int) = WebcastProto.Gift(
            msgId = 1L,
            createTime = 0L,
            nickname = "TokLover",
            uniqueId = "toklover",
            giftName = "Rosa",
            giftId = 5655L,
            repeatCount = 1,
            comboCount = 1,
            repeatEnd = repeatEnd,
            combo = combo,
            diamondCount = 1
        )

        assertFalse(
            "Un paso intermedio del combo no debe publicarse",
            client.shouldEmitGift(sample(combo = true, repeatEnd = 0))
        )
        assertTrue(
            "El cierre del combo sí debe publicarse",
            client.shouldEmitGift(sample(combo = true, repeatEnd = 1))
        )
        assertTrue(
            "Un regalo suelto (sin combo) se publica siempre",
            client.shouldEmitGift(sample(combo = false, repeatEnd = 0))
        )
    }

    /**
     * Construye un payload aplicando [block] sobre un escritor nuevo.
     *
     * Mini escritor protobuf para los tests. Escribe tags como
     * `(campo << 3) | wire type` y varints en base 128, igual que el protocolo
     * real, así los tests ejercitan el mismo lector que usa la app sin
     * depender de `protoc` ni de capturas binarias opacas.
     */
    private fun proto(block: ProtoFixture.() -> Unit): ByteArray {
        val fixture = ProtoFixture()
        fixture.block()
        return fixture.bytes()
    }

    private class ProtoFixture {
        private val out = ByteArrayOutputStream()

        fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

        fun bytes(field: Int, value: ByteArray) {
            varint(((field shl 3) or 2).toLong())
            varint(value.size.toLong())
            out.write(value)
        }

        fun varint(field: Int, value: Long) {
            varint(((field shl 3) or 0).toLong())
            varint(value)
        }

        fun bytes(): ByteArray = out.toByteArray()

        private fun varint(value: Long) {
            var remaining = value
            while (true) {
                val byte = (remaining and 0x7F).toInt()
                remaining = remaining ushr 7
                if (remaining == 0L) {
                    out.write(byte)
                    return
                }
                out.write(byte or 0x80)
            }
        }
    }
}
