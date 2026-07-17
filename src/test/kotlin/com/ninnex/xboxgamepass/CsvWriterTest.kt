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
    fun `writes BOM CRLF headers and lowercase booleans`() {
        val csv = CsvWriter.sourceCsv(listOf(GameRow("Game", console = true, pc = false)))

        assertTrue(csv.startsWith("\uFEFFname,console,pc\r\n"))
        assertTrue(csv.endsWith("Game,true,false\r\n"))
        assertFalse(Regex("(?<!\\r)\\n").containsMatchIn(csv))
    }

    @Test
    fun `rejects rows whose width differs from the header`() {
        assertFailsWith<IllegalArgumentException> {
            CsvWriter.toCsv(listOf("one", "two"), listOf(listOf("only one")))
        }
    }

    @Test
    fun `creates exactly the six output files in contract order`() {
        val catalogs = Catalogs(
            ultimate = games("Ultimate"),
            premium = games("Premium"),
            eaPlay = games("EA"),
            ubisoftPlus = games("Ubisoft"),
        )
        val processed = ProcessedCatalogs(
            ultimateNoPremium = listOf(processed("Ultimate")),
            ultimateExclusive = listOf(processed("Ultimate")),
        )

        assertEquals(
            listOf(
                "ultimate.csv",
                "premium.csv",
                "ea-play.csv",
                "ubisoft-plus.csv",
                "ultimate-no-premium.csv",
                "ultimate-exclusive.csv",
            ),
            CsvWriter.createFiles(catalogs, processed).map { it.name },
        )
    }

    private fun games(name: String) = listOf(GameRow(name, console = true, pc = false))

    private fun processed(name: String) =
        ProcessedGameRow(name, console = true, pc = false, AppConfig.ULTIMATE_EXCLUSIVE)
}
