package com.ninnex.xboxgamepass

import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class XboxGamePassGenerator(
    private val client: XboxCatalogClient = XboxClient(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun generate(outputDirectory: Path): GenerationResult {
        val loadedCatalogs = loadCatalogSources()
        val cloudProductIds = client.loadCloudProductIds()
        val allProductIds = AppConfig.catalogs.flatMap { catalog ->
            loadedCatalogs.getValue(catalog.key).flatMap { it.ids }
        }
        val products = client.loadProducts(allProductIds)
        val essentialSources = EssentialCatalogFilter.filter(
            loadedCatalogs.getValue("essential"),
            products,
        )

        fun rows(key: String): List<GameRow> = CatalogProcessor.buildCatalogRows(
            if (key == "essential") essentialSources else loadedCatalogs.getValue(key),
            products,
            cloudProductIds,
        )

        val rawCatalogs = Catalogs(
            ultimate = rows("ultimate"),
            premium = rows("premium"),
            essential = rows("essential"),
            eaPlay = rows("eaPlay"),
            ubisoftPlus = rows("ubisoftPlus"),
        )
        logUnmatchedCloudProductIds(rawCatalogs, cloudProductIds)
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

    private fun logUnmatchedCloudProductIds(catalogs: Catalogs, cloudProductIds: Set<String>) {
        val publishedIds = sequenceOf(
            catalogs.ultimate,
            catalogs.premium,
            catalogs.essential,
            catalogs.eaPlay,
            catalogs.ubisoftPlus,
        ).flatten().mapTo(hashSetOf()) { it.productId }
        val unmatched = (cloudProductIds - publishedIds).sorted()
        if (unmatched.isNotEmpty()) {
            println(
                "[Cloud] ${unmatched.size} Cloud Product IDs have no published row; " +
                    "sample: ${unmatched.take(20).joinToString(", ")}",
            )
        }
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

internal object EssentialCatalogFilter {
    fun filter(
        platformLists: List<PlatformProductIds>,
        products: Map<String, ProductMetadata>,
    ): List<PlatformProductIds> {
        val essentialIds = platformLists
            .flatMapTo(linkedSetOf()) { it.ids }
        val unknownIds = essentialIds
            .filter { products[it]?.priceStatus != PriceStatus.FREE &&
                products[it]?.priceStatus != PriceStatus.PAID }
            .sorted()
        check(unknownIds.isEmpty()) {
            "Essential product price could not be determined. " +
                "Product IDs: ${unknownIds.joinToString(", ")}. " +
                "No catalog files were written."
        }

        val freeIds = essentialIds.filterTo(hashSetOf()) {
            products.getValue(it).priceStatus == PriceStatus.FREE
        }
        println(
            "[Phase B] Essential: excluded ${freeIds.size} confirmed free product IDs.",
        )
        return platformLists.map { platformList ->
            platformList.copy(ids = platformList.ids.filterNot(freeIds::contains))
        }
    }
}
