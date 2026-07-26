package com.ninnex.xboxgamepass

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogInfoWriterTest {
    @Test
    fun `round trips stable catalog information`() {
        val info = CatalogInfo(
            AppConfig.XBOX_STORE_BASE_URL,
            20,
            Instant.parse("2026-07-25T14:42:15Z").toString(),
            changesFound = true,
        )
        val json = CatalogInfoWriter.write(info)

        assertEquals(info, CatalogInfoWriter.parse(json))
        assertTrue(json.indexOf("xboxStoreBaseUrl") < json.indexOf("newGameDisplayDays"))
        assertTrue(json.indexOf("newGameDisplayDays") < json.indexOf("lastCheckedAt"))
        assertTrue(json.indexOf("lastCheckedAt") < json.indexOf("changesFound"))
    }

    @Test
    fun `rejects non-positive display duration`() {
        val json = """
            {
              "xboxStoreBaseUrl": "${AppConfig.XBOX_STORE_BASE_URL}",
              "newGameDisplayDays": 0,
              "lastCheckedAt": "2026-07-25T14:42:15Z",
              "changesFound": false
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> { CatalogInfoWriter.parse(json) }
    }
}
