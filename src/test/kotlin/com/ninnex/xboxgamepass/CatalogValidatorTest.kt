package com.ninnex.xboxgamepass

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CatalogValidatorTest {
    @Test
    fun `rejects BOM and CRLF`() {
        val valid = CsvWriter.sourceCsv(
            listOf(GameRow("Game", "9TEST0000000", true, false, "game/9TEST0000000")),
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
        val header = "name,productId,console,pc,storePath,newSinceDate\n"
        val invalidRows = listOf(
            "Game,9TEST0000000,yes,false,game/9TEST0000000,\n",
            "Game,9TEST0000000,true,false,https://www.xbox.com/game,2026-07-25\n",
            "Game,9TEST0000000,true,false,game/9TEST0000000,2026-02-30\n",
        )
        invalidRows.forEach { row ->
            assertFailsWith<IllegalArgumentException> {
                CatalogValidator.validateCsv("ultimate.csv", header + row)
            }
        }
    }
}
