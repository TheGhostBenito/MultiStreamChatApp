package com.example.chatmultistream.data.client

import com.example.chatmultistream.model.ChatEvent
import com.example.chatmultistream.model.ChatEventType
import com.example.chatmultistream.model.Platform
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeParserTest {

    private val client = YouTubeChatClient()

    @Test
    fun parsea_mensaje_normal() {
        val item = JSONObject(
            """
            {
              "liveChatTextMessageRenderer": {
                "authorName": { "simpleText": "Ana" },
                "message": { "runs": [ { "text": "hola " }, { "text": "mundo" } ] }
              }
            }
            """.trimIndent()
        )

        val message = client.parseChatItem(item)
        assertEquals(Platform.YOUTUBE, message?.platform)
        assertEquals("Ana", message?.username)
        assertEquals("hola mundo", message?.message)
        assertNull(message?.event)
    }

    @Test
    fun parsea_super_chat_con_importe() {
        val item = JSONObject(
            """
            {
              "liveChatPaidMessageRenderer": {
                "authorName": { "simpleText": "Carlos" },
                "purchaseAmountText": { "simpleText": "${'$'}5.00" },
                "message": { "runs": [ { "text": "gran stream" } ] }
              }
            }
            """.trimIndent()
        )

        val message = client.parseChatItem(item)
        assertEquals("Carlos", message?.username)
        assertEquals("gran stream", message?.message)
        assertEquals(
            ChatEvent(type = ChatEventType.DONATION, amountText = "$5.00"),
            message?.event
        )
    }

    @Test
    fun parsea_super_sticker() {
        val item = JSONObject(
            """
            {
              "liveChatPaidStickerRenderer": {
                "authorName": { "simpleText": "Lucia" },
                "purchaseAmountText": { "simpleText": "MX${'$'}100.00" }
              }
            }
            """.trimIndent()
        )

        val message = client.parseChatItem(item)
        assertEquals("Lucia", message?.username)
        assertEquals("", message?.message)
        assertEquals(
            ChatEvent(type = ChatEventType.SUPER_STICKER, amountText = "MX$100.00"),
            message?.event
        )
    }

    @Test
    fun parsea_membresia() {
        val item = JSONObject(
            """
            {
              "liveChatMembershipItemRenderer": {
                "authorName": { "simpleText": "Marta" },
                "message": { "runs": [ { "text": "Member for 6 months" } ] }
              }
            }
            """.trimIndent()
        )

        val message = client.parseChatItem(item)
        assertEquals("Marta", message?.username)
        assertEquals("Member for 6 months", message?.message)
        assertEquals(ChatEvent(type = ChatEventType.MEMBERSHIP), message?.event)
    }

    @Test
    fun parsea_regalo_de_membresias_con_cantidad() {
        val item = JSONObject(
            """
            {
              "liveChatSponsorshipsGiftPurchaseAnnouncementRenderer": {
                "header": {
                  "liveChatSponsorshipsHeaderRenderer": {
                    "authorName": { "simpleText": "Pedro" },
                    "primaryText": { "runs": [ { "text": "Gifted 5 memberships" } ] }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val message = client.parseChatItem(item)
        assertEquals("Pedro", message?.username)
        assertEquals(
            ChatEvent(type = ChatEventType.GIFT_MEMBERSHIP, count = 5),
            message?.event
        )
    }

    @Test
    fun parsea_membresia_regalada_recibida() {
        val item = JSONObject(
            """
            {
              "liveChatSponsorshipsGiftRedemptionAnnouncementRenderer": {
                "authorName": { "simpleText": "Sofia" },
                "message": {
                  "runs": [ { "text": "received a gift membership from Pedro" } ]
                }
              }
            }
            """.trimIndent()
        )

        val message = client.parseChatItem(item)
        assertEquals("Sofia", message?.username)
        assertEquals(
            ChatEvent(type = ChatEventType.GIFT_MEMBERSHIP, count = 1),
            message?.event
        )
    }

    @Test
    fun ignora_items_desconocidos() {
        assertNull(client.parseChatItem(JSONObject("""{"liveChatViewerEngagementMessageRenderer":{}}""")))
    }
}
