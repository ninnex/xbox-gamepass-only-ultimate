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

        println("[Phase B] $catalogName ${source.platform.logName()}: ${ids.size} product IDs.")
        return PlatformProductIds(source.platform, ids.toList())
    }

    fun loadProducts(productIds: List<String>): Map<String, ProductMetadata> {
        val uniqueIds = productIds.mapTo(linkedSetOf()) { it.uppercase(Locale.ROOT) }.toList()
        check(uniqueIds.isNotEmpty()) { "No Product IDs were supplied." }
        println(
            "[Phase B] Resolving ${uniqueIds.size} unique products in one Display Catalog request.",
        )

        val resolvedProducts = linkedMapOf<String, ResolvedProduct>()
        loadProductSet(uniqueIds).forEach { product ->
            addResolvedProduct(resolvedProducts, product)
        }

        val requestedIds = uniqueIds.toSet()
        val missingIds = requestedIds - resolvedProducts.keys
        val unexpectedIds = resolvedProducts.keys - requestedIds
        check(missingIds.isEmpty()) {
            "${missingIds.size} products could not be resolved. No files were written. " +
                "Missing IDs: ${missingIds.take(20).joinToString(", ")}"
        }
        check(unexpectedIds.isEmpty()) {
            "Display Catalog returned ${unexpectedIds.size} unexpected products. No files were written. " +
                "Unexpected IDs: ${unexpectedIds.take(20).joinToString(", ")}"
        }

        val storePathCount = resolvedProducts.values.count { it.storePath != null }
        println(
            "[Phase B] Structured metadata supplied $storePathCount/${resolvedProducts.size} store paths; " +
                "using official Product ID routes for the remainder.",
        )
        return resolveStorePaths(resolvedProducts)
    }

    private fun loadProductSet(ids: List<String>): List<JsonNode> {
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
            "complete product set",
        )
        val products = data.get("Products")
        check(products?.isArray == true) { "Display Catalog has an invalid response." }
        return products.toList()
    }

    private fun addResolvedProduct(
        target: MutableMap<String, ResolvedProduct>,
        product: JsonNode,
    ) {
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
        if (id.isNotEmpty() && name.isNotEmpty()) {
            target[id] = ResolvedProduct(
                productId = id,
                productTitle = name,
                storePath = findStructuredStorePath(product, id),
            )
        }
    }

    private fun findStructuredStorePath(product: JsonNode, productId: String): String? {
        val pending = ArrayDeque<JsonNode>()
        pending.add(product)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            when {
                node.isTextual -> {
                    val candidate = node.asText().trim()
                    if (candidate.startsWith("https://", ignoreCase = true)) {
                        runCatching { StorePath.fromOfficialUrl(candidate, productId) }
                            .getOrNull()
                            ?.let { return it }
                    }
                }
                node.isContainerNode -> node.forEach(pending::addLast)
            }
        }
        return null
    }

    private fun resolveStorePaths(
        products: Map<String, ResolvedProduct>,
    ): Map<String, ProductMetadata> = products.values
        .map { product ->
            ProductMetadata(
                productId = product.productId,
                productTitle = product.productTitle,
                storePath = product.storePath ?: StorePath.fromProductId(product.productId),
            )
        }
        .associateByTo(linkedMapOf()) { it.productId }

    private fun fetchJson(uri: URI, label: String): JsonNode {
        return objectMapper.readTree(fetchText(uri, label))
    }

    private fun fetchText(uri: URI, label: String): String {
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
                return response.body()
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

    private data class ResolvedProduct(
        val productId: String,
        val productTitle: String,
        val storePath: String?,
    )
}
