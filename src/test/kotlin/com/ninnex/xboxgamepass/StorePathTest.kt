package com.ninnex.xboxgamepass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StorePathTest {
    @Test
    fun `builds a validated official fallback route from any Product ID`() {
        val storePath = StorePath.fromProductId("9test0000000")

        assertEquals("-/9TEST0000000", storePath)
        StorePath.validate(storePath, "9TEST0000000")
    }

    @Test
    fun `extracts canonical store path and uppercases Product ID`() {
        assertEquals(
            "doom-the-dark-ages/9PH9X076T3Q4",
            StorePath.fromOfficialUrl(
                "https://www.xbox.com/en-US/games/store/doom-the-dark-ages/9ph9x076t3q4",
                "9PH9X076T3Q4",
            ),
        )
    }

    @Test
    fun `rejects insecure foreign mismatched and unsafe URLs`() {
        val invalidUrls = listOf(
            "http://www.xbox.com/en-US/games/store/game/9PH9X076T3Q4",
            "https://example.com/en-US/games/store/game/9PH9X076T3Q4",
            "https://www.xbox.com/en-US/play/game/9PH9X076T3Q4",
            "https://www.xbox.com/en-US/games/store/game/9OTHER000000",
            "https://www.xbox.com/en-US/games/store/game/9PH9X076T3Q4?source=test",
            "https://www.xbox.com/en-US/games/store/../9PH9X076T3Q4",
        )
        invalidUrls.forEach { url ->
            assertFailsWith<IllegalArgumentException>(url) {
                StorePath.fromOfficialUrl(url, "9PH9X076T3Q4")
            }
        }
    }
}
