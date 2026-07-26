package com.ninnex.xboxgamepass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogProcessorTest {
    @Test
    fun `merges availability and selects a shared Product ID`() {
        val rows = CatalogProcessor.buildCatalogRows(
            listOf(
                PlatformProductIds(Platform.CONSOLE, listOf("A", "B")),
                PlatformProductIds(Platform.PC, listOf("A", "C")),
            ),
            products("A" to "Alpha", "B" to "Beta", "C" to "Charlie"),
        )

        assertEquals(
            listOf(
                GameRow("Alpha", "A", console = true, pc = true, "alpha/A"),
                GameRow("Beta", "B", console = true, pc = false, "beta/B"),
                GameRow("Charlie", "C", console = false, pc = true, "charlie/C"),
            ),
            rows,
        )
    }

    @Test
    fun `prefers console Product ID when platform editions differ`() {
        val rows = CatalogProcessor.buildCatalogRows(
            listOf(
                PlatformProductIds(Platform.CONSOLE, listOf("CONSOLE")),
                PlatformProductIds(Platform.PC, listOf("PC")),
            ),
            products("CONSOLE" to "Same title", "PC" to "Same title"),
        )

        assertEquals("CONSOLE", rows.single().productId)
        assertTrue(rows.single().console)
        assertTrue(rows.single().pc)
    }

    @Test
    fun `deduplicates exact titles and sorts with the English comparator`() {
        val rows = CatalogProcessor.buildCatalogRows(
            listOf(PlatformProductIds(Platform.CONSOLE, listOf("1", "2", "3", "4"))),
            products("1" to "zulu", "2" to "Alpha", "3" to "beta", "4" to "Alpha"),
        )

        assertEquals(listOf("Alpha", "beta", "zulu"), rows.map { it.name })
    }

    @Test
    fun `matches JavaScript en-US localeCompare ordering`() {
        val names = listOf(
            "Abiotic Factor",
            "A Way Out",
            "A Plague Tale: Requiem",
            "A Game About Digging A Hole™",
            "Crysis® 2 Maximum Edition",
            "Crysis 3",
            "Crysis®",
            "Crysis 2",
            "Crysis® 3",
        )

        assertEquals(
            listOf(
                "A Game About Digging A Hole™",
                "A Plague Tale: Requiem",
                "A Way Out",
                "Abiotic Factor",
                "Crysis 2",
                "Crysis 3",
                "Crysis®",
                "Crysis® 2 Maximum Edition",
                "Crysis® 3",
            ),
            names.sortedWith(CatalogProcessor.nameComparator),
        )
    }

    @Test
    fun `fails when any Product ID has no resolved metadata`() {
        val error = assertFailsWith<IllegalStateException> {
            CatalogProcessor.buildCatalogRows(
                listOf(PlatformProductIds(Platform.PC, listOf("MISSING"))),
                emptyMap(),
            )
        }
        assertTrue(error.message.orEmpty().contains("MISSING"))
    }

    @Test
    fun `subtracts Premium and applies EA Play before Ubisoft priority`() {
        val catalogs = Catalogs(
            ultimate = games("Premium game", "EA game", "Both game", "Ubisoft game", "Exclusive game"),
            premium = games("Premium game"),
            essential = games("Essential game"),
            eaPlay = games("EA game", "Both game"),
            ubisoftPlus = games("Both game", "Ubisoft game"),
        )

        val processed = CatalogProcessor.buildProcessedRows(catalogs)

        assertEquals(
            listOf(
                "EA game" to AppConfig.EA_PLAY,
                "Both game" to AppConfig.EA_PLAY,
                "Ubisoft game" to AppConfig.UBISOFT_PLUS,
                "Exclusive game" to AppConfig.ULTIMATE_EXCLUSIVE,
            ),
            processed.ultimateNoPremium.map { it.name to it.category },
        )
        assertEquals(listOf("Exclusive game"), processed.ultimateExclusive.map { it.name })
        assertFalse(processed.ultimateNoPremium.any { it.name == "Premium game" })
    }

    @Test
    fun `accepts a complete valid result`() {
        val catalogs = Catalogs(
            ultimate = games("Premium", "EA", "Ubisoft", "Exclusive"),
            premium = games("Premium"),
            essential = games("Essential"),
            eaPlay = games("EA"),
            ubisoftPlus = games("Ubisoft"),
        )
        val processed = CatalogProcessor.buildProcessedRows(catalogs)

        CatalogProcessor.validateRows(catalogs, processed)
        assertTrue(processed.ultimateExclusive.isNotEmpty())
    }

    @Test
    fun `rejects empty source catalogs and invalid rows`() {
        val valid = games("Valid")
        val processed = ProcessedCatalogs(
            ultimateNoPremium = listOf(processed("Exclusive")),
            ultimateExclusive = listOf(processed("Exclusive")),
        )

        assertFailsWith<IllegalArgumentException> {
            CatalogProcessor.validateRows(
                Catalogs(emptyList(), valid, valid, valid, valid),
                processed,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogProcessor.validateRows(
                Catalogs(
                    listOf(
                        GameRow(
                            "Broken",
                            "9BROKEN00001",
                            false,
                            false,
                            "broken/9BROKEN00001",
                        ),
                    ),
                    valid,
                    valid,
                    valid,
                    valid,
                ),
                processed,
            )
        }
    }

    @Test
    fun `rejects an allowed category when membership does not match`() {
        val catalogs = Catalogs(
            ultimate = games("EA", "Exclusive"),
            premium = games("Premium"),
            essential = games("Essential"),
            eaPlay = games("EA"),
            ubisoftPlus = games("Ubisoft"),
        )
        val incorrectlyClassified = processed("EA").copy(category = AppConfig.UBISOFT_PLUS)

        assertFailsWith<IllegalArgumentException> {
            CatalogProcessor.validateRows(
                catalogs,
                ProcessedCatalogs(
                    ultimateNoPremium = listOf(incorrectlyClassified, processed("Exclusive")),
                    ultimateExclusive = listOf(processed("Exclusive")),
                ),
            )
        }
    }

    private fun games(vararg names: String): List<GameRow> =
        names.mapIndexed { index, name ->
            val productId = "9TEST${index.toString().padStart(7, '0')}"
            GameRow(name, productId, true, false, "game-$index/$productId")
        }

    private fun processed(name: String): ProcessedGameRow {
        val productId = "9TEST0000000"
        return ProcessedGameRow(
            name,
            productId,
            true,
            false,
            AppConfig.ULTIMATE_EXCLUSIVE,
            "game/$productId",
        )
    }

    private fun products(vararg values: Pair<String, String>): Map<String, ProductMetadata> =
        values.associate { (id, title) ->
            id to ProductMetadata(id, title, "${title.lowercase().replace(' ', '-')}/$id")
        }
}
