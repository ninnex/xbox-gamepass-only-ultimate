package com.ninnex.xboxgamepass

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XboxClientTest {
    @Test
    fun `loads normalized deduplicated Cloud IDs with the configured request`() {
        var requestedUri: URI? = null
        var requestedLabel = ""
        val client = XboxClient(textFetcher = { uri, label ->
            requestedUri = uri
            requestedLabel = label
            """[{"id":" 9cloud000001 "},{"id":"9CLOUD000001"},{"id":""},{"id":7}]"""
        })

        assertEquals(setOf("9CLOUD000001"), client.loadCloudProductIds())
        assertEquals("Xbox Cloud Gaming catalog", requestedLabel)
        assertEquals(
            "${AppConfig.SIGL_ENDPOINT}?" +
                "id=${AppConfig.CLOUD_LIST_ID}&" +
                "language=${AppConfig.LANGUAGE}&" +
                "market=${AppConfig.MARKET}&" +
                "platformContext=ConsoleGen8%3BConsoleGen9%3Bpc&" +
                "subscriptionContext=${AppConfig.CLOUD_SUBSCRIPTION_CONTEXT}",
            requestedUri.toString(),
        )
    }

    @Test
    fun `rejects empty or non-list Cloud responses`() {
        listOf("[]", "{}").forEach { response ->
            val error = assertFailsWith<IllegalStateException> {
                XboxClient(textFetcher = { _, _ -> response }).loadCloudProductIds()
            }
            assertTrue(error.message.orEmpty().contains("Cloud"))
        }
    }

    @Test
    fun `propagates Cloud request failures`() {
        assertFailsWith<IllegalStateException> {
            XboxClient(textFetcher = { _, _ -> error("source unavailable") })
                .loadCloudProductIds()
        }
    }
}
