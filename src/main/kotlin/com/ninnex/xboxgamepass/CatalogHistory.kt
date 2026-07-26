package com.ninnex.xboxgamepass

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

object CatalogHistory {
    data class Result(
        val catalogs: Catalogs,
        val processed: ProcessedCatalogs,
        val migrationBaseline: Boolean,
    )

    fun applyNewSinceDates(
        outputDirectory: Path,
        catalogs: Catalogs,
        processed: ProcessedCatalogs,
        today: LocalDate,
    ): Result {
        val baseline = detectMigrationBaseline(outputDirectory)
        if (baseline) {
            return Result(
                catalogs = catalogs.clearNewSinceDates(),
                processed = processed.clearNewSinceDates(),
                migrationBaseline = true,
            )
        }

        fun previousDates(fileName: String): Map<String, String> {
            val content = Files.readString(outputDirectory.resolve(fileName), StandardCharsets.UTF_8)
            CatalogValidator.validateCsv(fileName, content, allowLegacyClassifiedFormat = true)
            return CsvReader.parse(content).drop(1).associate { row -> row[1] to row.last() }
        }

        fun dateRows(fileName: String, rows: List<GameRow>): List<GameRow> {
            val previous = previousDates(fileName)
            return rows.map { row ->
                row.copy(newSinceDate = previous[row.productId] ?: today.toString())
            }
        }

        fun dateProcessedRows(
            fileName: String,
            rows: List<ProcessedGameRow>,
        ): List<ProcessedGameRow> {
            val previous = previousDates(fileName)
            return rows.map { row ->
                row.copy(newSinceDate = previous[row.productId] ?: today.toString())
            }
        }

        return Result(
            catalogs = catalogs.copy(
                ultimate = dateRows("ultimate.csv", catalogs.ultimate),
                premium = dateRows("premium.csv", catalogs.premium),
                essential = dateRows("essential.csv", catalogs.essential),
                eaPlay = catalogs.eaPlay.map { it.copy(newSinceDate = "") },
                ubisoftPlus = catalogs.ubisoftPlus.map { it.copy(newSinceDate = "") },
            ),
            processed = processed.copy(
                ultimate = dateProcessedRows("ultimate.csv", processed.ultimate),
                premium = dateProcessedRows("premium.csv", processed.premium),
                essential = dateProcessedRows("essential.csv", processed.essential),
                ultimateNoPremium = dateProcessedRows(
                    "ultimate-no-premium.csv",
                    processed.ultimateNoPremium,
                ),
                ultimateExclusive = dateProcessedRows(
                    "ultimate-exclusive.csv",
                    processed.ultimateExclusive,
                ),
            ),
            migrationBaseline = false,
        )
    }

    fun changesFound(outputDirectory: Path, csvFiles: List<GeneratedFile>): Boolean {
        require(csvFiles.map { it.name }.toSet() == AppConfig.expectedCsvFileNames) {
            "changesFound requires exactly the seven CSV files."
        }
        return csvFiles.any { candidate ->
            val published = outputDirectory.resolve(candidate.name)
            !Files.exists(published) ||
                Files.readString(published, StandardCharsets.UTF_8) != candidate.content
        }
    }

    private fun detectMigrationBaseline(outputDirectory: Path): Boolean {
        if (!Files.exists(outputDirectory)) return true
        val actualCsvNames = Files.list(outputDirectory).use { files ->
            files.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".csv") }
                .map { it.fileName.toString() }
                .toList()
                .toSet()
        }
        if (actualCsvNames.isEmpty()) return true
        if (actualCsvNames == AppConfig.expectedCsvFileNames) {
            actualCsvNames.forEach { fileName ->
                CatalogValidator.validateCsv(
                    fileName,
                    Files.readString(outputDirectory.resolve(fileName), StandardCharsets.UTF_8),
                    allowLegacyClassifiedFormat = true,
                )
            }
            return false
        }

        val legacyFileNames = AppConfig.expectedCsvFileNames - "essential.csv"
        if (actualCsvNames == legacyFileNames && legacyFileNames.all { fileName ->
                val firstLine = Files.readAllLines(outputDirectory.resolve(fileName)).firstOrNull()
                    ?.removePrefix("\uFEFF")
                    ?.removeSuffix("\r")
                val expected = if (
                    fileName == "ultimate-no-premium.csv" ||
                    fileName == "ultimate-exclusive.csv"
                ) {
                    "name,console,pc,category"
                } else {
                    "name,console,pc"
                }
                firstLine == expected
            }
        ) {
            return true
        }
        throw IllegalStateException(
            "Published data is neither a complete current contract nor the supported legacy baseline.",
        )
    }

    private fun Catalogs.clearNewSinceDates(): Catalogs = copy(
        ultimate = ultimate.map { it.copy(newSinceDate = "") },
        premium = premium.map { it.copy(newSinceDate = "") },
        essential = essential.map { it.copy(newSinceDate = "") },
        eaPlay = eaPlay.map { it.copy(newSinceDate = "") },
        ubisoftPlus = ubisoftPlus.map { it.copy(newSinceDate = "") },
    )

    private fun ProcessedCatalogs.clearNewSinceDates(): ProcessedCatalogs = copy(
        ultimate = ultimate.map { it.copy(newSinceDate = "") },
        premium = premium.map { it.copy(newSinceDate = "") },
        essential = essential.map { it.copy(newSinceDate = "") },
        ultimateNoPremium = ultimateNoPremium.map { it.copy(newSinceDate = "") },
        ultimateExclusive = ultimateExclusive.map { it.copy(newSinceDate = "") },
    )
}
