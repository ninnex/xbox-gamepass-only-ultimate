package com.ninnex.xboxgamepass

import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class XboxGamePassGenerator(
    private val client: XboxClient = XboxClient(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun generate(outputDirectory: Path): GenerationResult {
        val loadedCatalogs = loadCatalogSources()
        val allProductIds = AppConfig.catalogs.flatMap { catalog ->
            loadedCatalogs.getValue(catalog.key).flatMap { it.ids }
        }
        val products = client.loadProducts(allProductIds)

        fun rows(key: String): List<GameRow> = CatalogProcessor.buildCatalogRows(
            loadedCatalogs.getValue(key),
            products,
        )

        val rawCatalogs = Catalogs(
            ultimate = rows("ultimate"),
            premium = rows("premium"),
            essential = rows("essential"),
            eaPlay = rows("eaPlay"),
            ubisoftPlus = rows("ubisoftPlus"),
        )
        val rawProcessed = CatalogProcessor.buildProcessedRows(rawCatalogs)
        val dated = CatalogHistory.applyNewSinceDates(
            outputDirectory = outputDirectory,
            catalogs = rawCatalogs,
            processed = rawProcessed,
            today = LocalDate.now(clock),
        )
        val catalogs = dated.catalogs
        val processed = dated.processed
        CatalogProcessor.validateRows(catalogs, processed)
        val csvFiles = CsvWriter.createFiles(catalogs, processed)
        csvFiles.forEach { CatalogValidator.validateCsv(it.name, it.content) }
        val changesFound = CatalogHistory.changesFound(outputDirectory, csvFiles)
        val catalogInfo = CatalogInfo(
            xboxStoreBaseUrl = AppConfig.XBOX_STORE_BASE_URL,
            newGameDisplayDays = AppConfig.NEW_GAME_DISPLAY_DAYS,
            lastCheckedAt = Instant.now(clock).toString(),
            changesFound = changesFound,
        )
        val files = csvFiles + GeneratedFile(
            "catalog-info.json",
            CatalogInfoWriter.write(catalogInfo),
        )
        CatalogValidator.validateFiles(files)
        if (dated.migrationBaseline) {
            println("[Phase B] New-game tracking baseline established without marking existing games.")
        }
        return GenerationResult(catalogs, processed, catalogInfo, files)
    }

    private fun loadCatalogSources(): Map<String, List<PlatformProductIds>> {
        val sourceCount = AppConfig.catalogs.sumOf { it.sources.size }
        val executor = Executors.newFixedThreadPool(sourceCount)
        return try {
            AppConfig.catalogs.associate { catalog ->
                val futures = catalog.sources.map { source ->
                    executor.submit(Callable { client.loadSigl(source, catalog.fileName.removeSuffix(".csv")) })
                }
                catalog.key to futures.map { future ->
                    try {
                        future.get()
                    } catch (error: ExecutionException) {
                        throw (error.cause as? Exception ?: error)
                    }
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
