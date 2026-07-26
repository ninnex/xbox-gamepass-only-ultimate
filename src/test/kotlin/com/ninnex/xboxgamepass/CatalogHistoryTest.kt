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
            val processed = processed(catalogs, "2026-07-01")

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
            val previousProcessed = processed(previousCatalogs, "2026-07-03")
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
                ultimate = listOf(
                    processedRow(previousCatalogs.ultimate.single()),
                    processedRow(added),
                ),
                premium = listOf(
                    processedRow(previousCatalogs.premium.single(), AppConfig.PREMIUM),
                ),
                essential = listOf(
                    processedRow(previousCatalogs.essential.single(), AppConfig.ESSENTIAL),
                ),
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
            val processed = processed(catalogs, "")
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

    @Test
    fun `migrates classified catalogs from the previous source schema without losing dates`() {
        val root = Files.createTempDirectory("history-schema-migration-")
        try {
            val previousCatalogs = catalogs("2026-07-01")
            val previousProcessed = processed(previousCatalogs, "2026-07-03")
            CsvWriter.createFiles(previousCatalogs, previousProcessed).forEach { file ->
                val content = when (file.name) {
                    "ultimate.csv" -> CsvWriter.sourceCsv(previousCatalogs.ultimate)
                    "premium.csv" -> CsvWriter.sourceCsv(previousCatalogs.premium)
                    "essential.csv" -> CsvWriter.sourceCsv(previousCatalogs.essential)
                    else -> file.content
                }
                Files.writeString(root.resolve(file.name), content)
            }

            val candidateCatalogs = catalogs("")
            val candidateProcessed = CatalogProcessor.buildProcessedRows(candidateCatalogs)
            val result = CatalogHistory.applyNewSinceDates(
                root,
                candidateCatalogs,
                candidateProcessed,
                LocalDate.parse("2026-07-25"),
            )

            assertFalse(result.migrationBaseline)
            assertEquals("2026-07-01", result.processed.ultimate.single().newSinceDate)
            assertEquals("2026-07-01", result.processed.premium.single().newSinceDate)
            assertEquals("2026-07-01", result.processed.essential.single().newSinceDate)
            assertEquals("2026-07-03", result.processed.ultimateNoPremium.single().newSinceDate)
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

    private fun processed(catalogs: Catalogs, differenceDate: String): ProcessedCatalogs =
        ProcessedCatalogs(
            ultimate = listOf(processedRow(catalogs.ultimate.single())),
            premium = listOf(processedRow(catalogs.premium.single(), AppConfig.PREMIUM)),
            essential = listOf(processedRow(catalogs.essential.single(), AppConfig.ESSENTIAL)),
            ultimateNoPremium = listOf(
                processedRow(catalogs.ultimate.single()).copy(newSinceDate = differenceDate),
            ),
            ultimateExclusive = listOf(
                processedRow(catalogs.ultimate.single()).copy(newSinceDate = differenceDate),
            ),
        )

    private fun game(name: String, id: String, date: String = "") =
        GameRow(name, id, true, false, "game/$id", date)

    private fun processedRow(
        game: GameRow,
        category: String = AppConfig.ULTIMATE_EXCLUSIVE,
    ) = ProcessedGameRow(
        game.name,
        game.productId,
        game.console,
        game.pc,
        category,
        game.storePath,
        game.newSinceDate,
    )

    private fun deleteRecursively(root: java.nio.file.Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
