package com.ninnex.xboxgamepass

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class XboxGamePassGenerator(
    private val client: XboxClient = XboxClient(),
) {
    fun generate(): GenerationResult {
        val loadedCatalogs = loadCatalogSources()
        val allProductIds = AppConfig.catalogs.flatMap { catalog ->
            loadedCatalogs.getValue(catalog.key).flatMap { it.ids }
        }
        val productNames = client.loadProductNames(allProductIds)

        fun rows(key: String): List<GameRow> = CatalogProcessor.buildCatalogRows(
            loadedCatalogs.getValue(key),
            productNames,
        )

        val catalogs = Catalogs(
            ultimate = rows("ultimate"),
            premium = rows("premium"),
            eaPlay = rows("eaPlay"),
            ubisoftPlus = rows("ubisoftPlus"),
        )
        val processed = CatalogProcessor.buildProcessedRows(catalogs)
        CatalogProcessor.validateRows(catalogs, processed)
        return GenerationResult(catalogs, processed, CsvWriter.createFiles(catalogs, processed))
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
