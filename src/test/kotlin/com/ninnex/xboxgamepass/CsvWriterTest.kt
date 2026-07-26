package com.ninnex.xboxgamepass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CsvWriterTest {
    @Test
    fun `escapes commas quotes carriage returns and line feeds`() {
        assertEquals("plain", CsvWriter.escapeCsv("plain"))
        assertEquals("\"with, comma\"", CsvWriter.escapeCsv("with, comma"))
        assertEquals("\"with \"\"quotes\"\"\"", CsvWriter.escapeCsv("with \"quotes\""))
        assertEquals("\"two\nlines\"", CsvWriter.escapeCsv("two\nlines"))
        assertEquals("\"two\rlines\"", CsvWriter.escapeCsv("two\rlines"))
    }

    @Test
    fun `writes UTF-8 contract with LF and no BOM`() {
        val csv = CsvWriter.sourceCsv(listOf(game("Game")))

        assertTrue(csv.startsWith("name,productId,console,pc,storePath,newSinceDate\n"))
        assertTrue(csv.endsWith("Game,9TEST0000000,true,false,game/9TEST0000000,\n"))
        assertFalse(csv.startsWith('\uFEFF'))
        assertFalse('\r' in csv)
    }

    @Test
    fun `rejects rows whose width differs from the header`() {
        assertFailsWith<IllegalArgumentException> {
            CsvWriter.toCsv(listOf("one", "two"), listOf(listOf("only one")))
        }
    }

    @Test
    fun `creates exactly seven CSV files in contract order`() {
        val catalogs = Catalogs(
            ultimate = listOf(game("Ultimate")),
            premium = listOf(game("Premium")),
            essential = listOf(game("Essential")),
            eaPlay = listOf(game("EA")),
            ubisoftPlus = listOf(game("Ubisoft")),
        )
        val processed = ProcessedCatalogs(
            ultimateNoPremium = listOf(processed("Ultimate")),
            ultimateExclusive = listOf(processed("Ultimate")),
        )

        assertEquals(
            listOf(
                "ultimate.csv",
                "premium.csv",
                "essential.csv",
                "ea-play.csv",
                "ubisoft-plus.csv",
                "ultimate-no-premium.csv",
                "ultimate-exclusive.csv",
            ),
            CsvWriter.createFiles(catalogs, processed).map { it.name },
        )
    }

    private fun game(name: String): GameRow =
        GameRow(name, "9TEST0000000", true, false, "game/9TEST0000000")

    private fun processed(name: String): ProcessedGameRow =
        ProcessedGameRow(
            name,
            "9TEST0000000",
            true,
            false,
            AppConfig.ULTIMATE_EXCLUSIVE,
            "game/9TEST0000000",
        )
}
