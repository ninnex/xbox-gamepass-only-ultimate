package com.ninnex.xboxgamepass

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
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

    @Test
    fun `generates valid unique Microsoft correlation vectors`() {
        val vectors = List(1_000) { MicrosoftCorrelationVector.generate() }

        assertEquals(vectors.size, vectors.toSet().size)
        vectors.forEach { vector ->
            assertTrue(vector.matches(Regex("[A-Za-z0-9+/]{22}\\.0")))
            val root = vector.substringBeforeLast('.')
            assertEquals(16, Base64.getDecoder().decode(root).size)
        }
    }

    @Test
    fun `uses a new Microsoft correlation vector for each metadata request`() {
        val requestedUris = mutableListOf<URI>()
        val client = XboxClient(textFetcher = { uri, _ ->
            requestedUris += uri
            val productId = queryParameter(uri, "bigIds")
            """{"Products":[{"ProductId":"$productId","LocalizedProperties":[{"ProductTitle":"Test game"}]}]}"""
        })

        client.loadProducts(listOf("9TEST0000001"))
        client.loadProducts(listOf("9TEST0000002"))

        val firstVector = queryParameter(requestedUris[0], "MS-CV")
        val secondVector = queryParameter(requestedUris[1], "MS-CV")
        assertTrue(firstVector.matches(Regex("[A-Za-z0-9+/]{22}\\.0")))
        assertTrue(secondVector.matches(Regex("[A-Za-z0-9+/]{22}\\.0")))
        assertNotEquals(firstVector, secondVector)
    }

    private fun queryParameter(uri: URI, name: String): String = uri.rawQuery
        .split('&')
        .map { parameter -> parameter.split('=', limit = 2) }
        .first { (key) -> URLDecoder.decode(key, StandardCharsets.UTF_8) == name }
        .let { (_, value) -> URLDecoder.decode(value, StandardCharsets.UTF_8) }
}
