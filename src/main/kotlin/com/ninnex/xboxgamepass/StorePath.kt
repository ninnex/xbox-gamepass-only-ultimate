package com.ninnex.xboxgamepass

import java.net.URI
import java.util.Locale

object StorePath {
    private const val STORE_MARKER = "/games/store/"
    private val productIdPattern = Regex("[A-Z0-9]{12}")

    fun fromOfficialUrl(url: String, expectedProductId: String): String {
        val expected = expectedProductId.trim().uppercase(Locale.ROOT)
        require(productIdPattern.matches(expected)) { "Invalid expected Product ID: $expectedProductId" }

        val uri = runCatching { URI.create(url.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid Xbox Store URL.", it) }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Xbox Store URL must use HTTPS." }
        require(uri.host.equals("www.xbox.com", ignoreCase = true)) {
            "Xbox Store URL must use www.xbox.com."
        }
        require(uri.rawQuery == null && uri.rawFragment == null) {
            "Xbox Store URL must not contain a query string or fragment."
        }

        val path = uri.path.orEmpty()
        val markerIndex = path.lowercase(Locale.ROOT).indexOf(STORE_MARKER)
        require(markerIndex >= 0) { "Xbox Store URL is outside /games/store/." }
        val rawStorePath = path.substring(markerIndex + STORE_MARKER.length).trim('/')
        require(rawStorePath.isNotEmpty() && !rawStorePath.contains("..")) {
            "Xbox Store path is empty or unsafe."
        }

        val segments = rawStorePath.split('/')
        require(segments.size >= 2 && segments.none(String::isBlank)) {
            "Xbox Store path must include a product slug and Product ID."
        }
        require(segments.last().equals(expected, ignoreCase = true)) {
            "Xbox Store URL does not match Product ID $expected."
        }

        return (segments.dropLast(1) + expected).joinToString("/")
    }

    fun validate(storePath: String, expectedProductId: String) {
        require(!storePath.startsWith('/')) { "storePath must be relative." }
        require(
            !storePath.contains("://") &&
                !storePath.contains('?') &&
                !storePath.contains('#') &&
                !storePath.contains(".."),
        ) { "storePath contains a forbidden component." }
        val rebuilt = URI.create(AppConfig.XBOX_STORE_BASE_URL).resolve(storePath).toString()
        require(fromOfficialUrl(rebuilt, expectedProductId) == storePath) {
            "storePath is not canonical."
        }
    }
}
