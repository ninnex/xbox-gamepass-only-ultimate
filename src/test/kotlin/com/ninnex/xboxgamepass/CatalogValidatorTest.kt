package com.ninnex.xboxgamepass

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CatalogValidatorTest {
    @Test
    fun `rejects BOM and CRLF`() {
        val valid = CsvWriter.sourceCsv(
            listOf(GameRow("Game", "9TEST0000000", true, false, true, "game/9TEST0000000")),
        )
        assertFailsWith<IllegalArgumentException> {
            CatalogValidator.validateCsv("ultimate.csv", "\uFEFF$valid")
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogValidator.validateCsv("ultimate.csv", valid.replace("\n", "\r\n"))
        }
    }

    @Test
    fun `rejects invalid dates booleans and store paths`() {
        val header = "name,productId,console,pc,cloud,category,storePath,newSinceDate\n"
        val invalidRows = listOf(
            "Game,9TEST0000000,yes,false,true,Essential,game/9TEST0000000,\n",
            "Game,9TEST0000000,true,false,yes,Essential,game/9TEST0000000,\n",
            "Game,9TEST0000000,true,false,true,Essential,https://www.xbox.com/game,2026-07-25\n",
            "Game,9TEST0000000,true,false,true,Essential,game/9TEST0000000,2026-02-30\n",
        )
        invalidRows.forEach { row ->
            assertFailsWith<IllegalArgumentException> {
                CatalogValidator.validateCsv("ultimate.csv", header + row)
            }
        }
    }

    @Test
    fun `accepts the previous source format only for classified migration reads`() {
        val legacy = CsvWriter.sourceCsv(
            listOf(GameRow("Game", "9TEST0000000", true, false, true, "game/9TEST0000000")),
        )

        assertFailsWith<IllegalArgumentException> {
            CatalogValidator.validateCsv("ultimate.csv", legacy)
        }
        CatalogValidator.validateCsv(
            "ultimate.csv",
            legacy,
            allowLegacyClassifiedFormat = true,
        )
    }

    @Test
    fun `accepts contracts without Cloud only for migration reads`() {
        val previousProcessed =
            "name,productId,console,pc,category,storePath,newSinceDate\n" +
                "Game,9TEST0000000,true,false,Essential,game/9TEST0000000,2026-07-25\n"
        val previousSource =
            "name,productId,console,pc,storePath,newSinceDate\n" +
                "Game,9TEST0000000,true,false,game/9TEST0000000,2026-07-25\n"

        assertFailsWith<IllegalArgumentException> {
            CatalogValidator.validateCsv("ultimate.csv", previousProcessed)
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogValidator.validateCsv("ea-play.csv", previousSource)
        }
        CatalogValidator.validateCsv(
            "ultimate.csv",
            previousProcessed,
            allowLegacyClassifiedFormat = true,
        )
        CatalogValidator.validateCsv(
            "ea-play.csv",
            previousSource,
            allowLegacyClassifiedFormat = true,
        )
    }
}
