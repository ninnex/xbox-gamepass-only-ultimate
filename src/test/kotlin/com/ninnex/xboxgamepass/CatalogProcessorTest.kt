package com.ninnex.xboxgamepass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogProcessorTest {
    @Test
    fun `merges console and PC availability by exact product title`() {
        val rows = CatalogProcessor.buildCatalogRows(
            listOf(
                PlatformProductIds(Platform.CONSOLE, listOf("A", "B")),
                PlatformProductIds(Platform.PC, listOf("A", "C")),
            ),
            mapOf("A" to "Alpha", "B" to "Beta", "C" to "Charlie"),
        )

        assertEquals(
            listOf(
                GameRow("Alpha", console = true, pc = true),
                GameRow("Beta", console = true, pc = false),
                GameRow("Charlie", console = false, pc = true),
            ),
            rows,
        )
    }

    @Test
    fun `deduplicates exact titles and sorts them using the English comparator`() {
        val rows = CatalogProcessor.buildCatalogRows(
            listOf(PlatformProductIds(Platform.CONSOLE, listOf("1", "2", "3", "4"))),
            mapOf("1" to "zulu", "2" to "Alpha", "3" to "beta", "4" to "Alpha"),
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
    fun `fails when any product ID has no resolved title`() {
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
    fun `comparison remains exact and case sensitive`() {
        val catalogs = Catalogs(
            ultimate = games("Game", "game", "Exclusive"),
            premium = games("Game"),
            eaPlay = games("EA placeholder"),
            ubisoftPlus = games("Ubisoft placeholder"),
        )

        val processed = CatalogProcessor.buildProcessedRows(catalogs)

        assertEquals(listOf("game", "Exclusive"), processed.ultimateNoPremium.map { it.name })
    }

    @Test
    fun `accepts a complete valid result`() {
        val catalogs = Catalogs(
            ultimate = games("Premium", "EA", "Ubisoft", "Exclusive"),
            premium = games("Premium"),
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
            CatalogProcessor.validateRows(Catalogs(emptyList(), valid, valid, valid), processed)
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogProcessor.validateRows(
                Catalogs(listOf(GameRow("Broken", false, false)), valid, valid, valid),
                processed,
            )
        }
    }

    @Test
    fun `rejects processed results that are empty or contain forbidden memberships`() {
        val catalogs = Catalogs(
            ultimate = games("Premium", "Exclusive"),
            premium = games("Premium"),
            eaPlay = games("EA"),
            ubisoftPlus = games("Ubisoft"),
        )

        assertFailsWith<IllegalArgumentException> {
            CatalogProcessor.validateRows(catalogs, ProcessedCatalogs(emptyList(), emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogProcessor.validateRows(
                catalogs,
                ProcessedCatalogs(
                    ultimateNoPremium = listOf(processed("Premium")),
                    ultimateExclusive = listOf(processed("Premium")),
                ),
            )
        }
    }

    @Test
    fun `rejects an allowed category when it does not match membership priority`() {
        val catalogs = Catalogs(
            ultimate = games("EA", "Exclusive"),
            premium = games("Premium"),
            eaPlay = games("EA"),
            ubisoftPlus = games("Ubisoft"),
        )
        val incorrectlyClassified = ProcessedGameRow(
            "EA",
            console = true,
            pc = false,
            category = AppConfig.UBISOFT_PLUS,
        )

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
        names.map { GameRow(it, console = true, pc = false) }

    private fun processed(name: String): ProcessedGameRow =
        ProcessedGameRow(name, console = true, pc = false, AppConfig.ULTIMATE_EXCLUSIVE)
}
