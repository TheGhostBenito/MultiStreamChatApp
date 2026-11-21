package com.example.chatmultistream

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.io.File

class KickTest {

    private val logFile = File("C:/Users/Benit/.gemini/antigravity-acp/brain/4b4adabc-2552-4acd-a954-578cfcd53c2f/scratch/kick_output.txt")
    private val client = OkHttpClient.Builder().build()
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    @Test
    fun testKickChannelData() {
        val slug = "westcol"
        val channelReq = Request.Builder()
            .url("https://kick.com/api/v2/channels/$slug")
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()

        client.newCall(channelReq).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            logFile.writeText(body)
        }
    }
}
