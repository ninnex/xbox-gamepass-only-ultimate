package com.ninnex.xboxgamepass

import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogHistoryTest {
    @Test
    fun `empty publication establishes a baseline without new dates`() {
        val root = Files.createTempDirectory("history-baseline-")
        try {
            val catalogs = catalogs("2026-07-01")
            val processed = processed(catalogs.ultimate.single(), "2026-07-01")

            val result = CatalogHistory.applyNewSinceDates(
                root.resolve("missing"),
                catalogs,
                processed,
                LocalDate.parse("2026-07-25"),
            )

            assertTrue(result.migrationBaseline)
            assertTrue(result.catalogs.ultimate.all { it.newSinceDate.isEmpty() })
            assertTrue(result.processed.ultimateExclusive.all { it.newSinceDate.isEmpty() })
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun `preserves existing dates and dates additions independently by list`() {
        val root = Files.createTempDirectory("history-current-")
        try {
            val previousCatalogs = catalogs("2026-07-01")
            val previousProcessed = processed(previousCatalogs.ultimate.single(), "2026-07-03")
            CsvWriter.createFiles(previousCatalogs, previousProcessed).forEach { file ->
                Files.writeString(root.resolve(file.name), file.content)
            }

            val added = game("Added", "9ADDED000001")
            val candidateCatalogs = previousCatalogs.copy(
                ultimate = previousCatalogs.ultimate.map { it.copy(newSinceDate = "") } + added,
                premium = previousCatalogs.premium.map { it.copy(newSinceDate = "") },
                essential = previousCatalogs.essential.map { it.copy(newSinceDate = "") },
            )
            val candidateProcessed = ProcessedCatalogs(
                ultimateNoPremium = listOf(
                    processedRow(previousCatalogs.ultimate.single()),
                    processedRow(added),
                ),
                ultimateExclusive = listOf(processedRow(added)),
            )

            val result = CatalogHistory.applyNewSinceDates(
                root,
                candidateCatalogs,
                candidateProcessed,
                LocalDate.parse("2026-07-25"),
            )

            assertFalse(result.migrationBaseline)
            assertEquals("2026-07-01", result.catalogs.ultimate.first().newSinceDate)
            assertEquals("2026-07-25", result.catalogs.ultimate.last().newSinceDate)
            assertEquals("2026-07-03", result.processed.ultimateNoPremium.first().newSinceDate)
            assertEquals("2026-07-25", result.processed.ultimateNoPremium.last().newSinceDate)
            assertEquals("2026-07-25", result.processed.ultimateExclusive.single().newSinceDate)
            assertTrue(result.catalogs.eaPlay.single().newSinceDate.isEmpty())
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun `changesFound compares exactly the seven CSV contents`() {
        val root = Files.createTempDirectory("history-changes-")
        try {
            val catalogs = catalogs("")
            val processed = processed(catalogs.ultimate.single(), "")
            val files = CsvWriter.createFiles(catalogs, processed)
            files.forEach { Files.writeString(root.resolve(it.name), it.content) }

            assertFalse(CatalogHistory.changesFound(root, files))
            assertTrue(
                CatalogHistory.changesFound(
                    root,
                    files.map {
                        if (it.name == "essential.csv") it.copy(content = it.content + "\n") else it
                    },
                ),
            )
        } finally {
            deleteRecursively(root)
        }
    }

    private fun catalogs(date: String): Catalogs = Catalogs(
        ultimate = listOf(game("Ultimate", "9ULTIMAT0001", date)),
        premium = listOf(game("Premium", "9PREMIUM0001", date)),
        essential = listOf(game("Essential", "9ESSENTI0001", date)),
        eaPlay = listOf(game("EA", "9EAPLAY00001", date)),
        ubisoftPlus = listOf(game("Ubisoft", "9UBISOFT0001", date)),
    )

    private fun processed(game: GameRow, date: String): ProcessedCatalogs {
        val row = processedRow(game).copy(newSinceDate = date)
        return ProcessedCatalogs(listOf(row), listOf(row))
    }

    private fun game(name: String, id: String, date: String = "") =
        GameRow(name, id, true, false, "game/$id", date)

    private fun processedRow(game: GameRow) = ProcessedGameRow(
        game.name,
        game.productId,
        game.console,
        game.pc,
        AppConfig.ULTIMATE_EXCLUSIVE,
        game.storePath,
    )

    private fun deleteRecursively(root: java.nio.file.Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
