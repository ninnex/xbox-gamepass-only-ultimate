package com.ninnex.xboxgamepass

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class XboxClient(
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(AppConfig.REQUEST_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    fun loadSigl(source: CatalogSource, catalogName: String): PlatformProductIds {
        val data = fetchJson(
            buildUri(
                AppConfig.SIGL_ENDPOINT,
                linkedMapOf(
                    "id" to source.id,
                    "language" to AppConfig.LANGUAGE,
                    "market" to AppConfig.MARKET,
                    "platformContext" to source.platformContext,
                    "subscriptionContext" to source.subscriptionContext,
                ),
            ),
            "$catalogName ${source.platform.logName()} catalog",
        )

        check(data.isArray) { "$catalogName ${source.platform.logName()} is not a list." }
        val ids = linkedSetOf<String>()
        data.forEach { item ->
            item.get("id")
                ?.takeIf(JsonNode::isTextual)
                ?.asText()
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.uppercase(Locale.ROOT)
                ?.let(ids::add)
        }
        check(ids.isNotEmpty()) { "$catalogName ${source.platform.logName()} returned no games." }

        println("[Stage 1B-A] $catalogName ${source.platform.logName()}: ${ids.size} product IDs.")
        return PlatformProductIds(source.platform, ids.toList())
    }

    fun loadProductNames(productIds: List<String>): Map<String, String> {
        val uniqueIds = productIds.mapTo(linkedSetOf()) { it.uppercase(Locale.ROOT) }.toList()
        val batches = uniqueIds.chunked(AppConfig.PRODUCT_BATCH_SIZE)
        println(
            "[Stage 1B-A] Resolving ${uniqueIds.size} unique products in ${batches.size} batches.",
        )

        val productNames = linkedMapOf<String, String>()
        loadBatches(batches, retry = false).flatten().forEach { product ->
            addResolvedProduct(productNames, product)
        }

        var missingIds = uniqueIds.filterNot(productNames::containsKey)
        if (missingIds.isNotEmpty()) {
            System.err.println(
                "[Stage 1B-A] Retrying ${missingIds.size} products that were not resolved.",
            )
            loadBatches(missingIds.chunked(AppConfig.PRODUCT_BATCH_SIZE), retry = true)
                .flatten()
                .forEach { product -> addResolvedProduct(productNames, product) }
            missingIds = uniqueIds.filterNot(productNames::containsKey)
        }

        check(missingIds.isEmpty()) {
            "${missingIds.size} products could not be resolved. No files were written. " +
                "Missing IDs: ${missingIds.take(20).joinToString(", ")}"
        }
        return productNames
    }

    private fun loadBatches(batches: List<List<String>>, retry: Boolean): List<List<JsonNode>> {
        if (batches.isEmpty()) return emptyList()
        val executor = Executors.newFixedThreadPool(minOf(AppConfig.PRODUCT_CONCURRENCY, batches.size))
        return try {
            val futures = batches.mapIndexed { index, ids ->
                val label = if (retry) "retry-${index + 1}" else "${index + 1}"
                executor.submit(Callable { loadProductBatch(ids, label) })
            }
            futures.map { future ->
                try {
                    future.get()
                } catch (error: ExecutionException) {
                    throw (error.cause as? Exception ?: error)
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun loadProductBatch(ids: List<String>, batchLabel: String): List<JsonNode> {
        val data = fetchJson(
            buildUri(
                AppConfig.PRODUCTS_ENDPOINT,
                linkedMapOf(
                    "bigIds" to ids.joinToString(","),
                    "market" to AppConfig.MARKET,
                    "languages" to AppConfig.LANGUAGE,
                    "MS-CV" to AppConfig.MS_CV,
                ),
            ),
            "product batch $batchLabel",
        )
        val products = data.get("Products")
        check(products?.isArray == true) { "Product batch $batchLabel has an invalid response." }
        return products.toList()
    }

    private fun addResolvedProduct(target: MutableMap<String, String>, product: JsonNode) {
        val id = product.get("ProductId")
            ?.takeIf(JsonNode::isTextual)
            ?.asText()
            ?.trim()
            ?.uppercase(Locale.ROOT)
            .orEmpty()
        val localizedProperties = product.get("LocalizedProperties")
        val name = if (localizedProperties?.isArray == true && localizedProperties.size() > 0) {
            localizedProperties[0].get("ProductTitle")
                ?.takeIf(JsonNode::isTextual)
                ?.asText()
                ?.trim()
                .orEmpty()
        } else {
            ""
        }
        if (id.isNotEmpty() && name.isNotEmpty()) target[id] = name
    }

    private fun fetchJson(uri: URI, label: String): JsonNode {
        var lastError: Exception? = null
        for (attempt in 1..AppConfig.REQUEST_ATTEMPTS) {
            try {
                val request = HttpRequest.newBuilder(uri)
                    .timeout(AppConfig.REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-store")
                    .header("User-Agent", "xbox-gamepass-csv-generator/1.0")
                    .GET()
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() !in 200..299) {
                    throw IOException("$label returned HTTP ${response.statusCode()}.")
                }
                return objectMapper.readTree(response.body())
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw error
            } catch (error: Exception) {
                lastError = error
                if (attempt < AppConfig.REQUEST_ATTEMPTS) Thread.sleep(750L * attempt)
            }
        }
        throw IOException(
            "$label failed after ${AppConfig.REQUEST_ATTEMPTS} attempts: ${lastError?.message}",
            lastError,
        )
    }

    private fun buildUri(base: String, parameters: Map<String, String>): URI {
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return URI.create("$base?$query")
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun Platform.logName(): String = name.lowercase(Locale.ROOT)
}
