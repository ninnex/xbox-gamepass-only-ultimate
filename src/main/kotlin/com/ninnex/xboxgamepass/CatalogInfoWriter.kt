package com.ninnex.xboxgamepass

import com.fasterxml.jackson.databind.ObjectMapper

object CatalogInfoWriter {
    private val objectMapper = ObjectMapper()

    fun write(info: CatalogInfo): String {
        val root = objectMapper.createObjectNode()
        root.put("xboxStoreBaseUrl", info.xboxStoreBaseUrl)
        root.put("newGameDisplayDays", info.newGameDisplayDays)
        root.put("lastCheckedAt", info.lastCheckedAt)
        root.put("changesFound", info.changesFound)
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n"
    }

    fun parse(content: String): CatalogInfo {
        val root = objectMapper.readTree(content)
        require(root.isObject) { "catalog-info.json must contain an object." }
        require(root.size() == 4) { "catalog-info.json contains unexpected fields." }

        val baseUrl = root.path("xboxStoreBaseUrl")
        val displayDays = root.path("newGameDisplayDays")
        val lastCheckedAt = root.path("lastCheckedAt")
        val changesFound = root.path("changesFound")
        require(baseUrl.isTextual && baseUrl.asText() == AppConfig.XBOX_STORE_BASE_URL) {
            "catalog-info.json contains an invalid Xbox Store base URL."
        }
        require(displayDays.isIntegralNumber && displayDays.canConvertToInt() && displayDays.asInt() > 0) {
            "newGameDisplayDays must be a positive integer."
        }
        require(lastCheckedAt.isTextual) { "lastCheckedAt must be an ISO-8601 instant." }
        val instant = runCatching { java.time.Instant.parse(lastCheckedAt.asText()) }.getOrNull()
        require(instant != null && instant.toString() == lastCheckedAt.asText()) {
            "lastCheckedAt must be a canonical ISO-8601 UTC instant."
        }
        require(changesFound.isBoolean) { "changesFound must be boolean." }

        return CatalogInfo(
            xboxStoreBaseUrl = baseUrl.asText(),
            newGameDisplayDays = displayDays.asInt(),
            lastCheckedAt = lastCheckedAt.asText(),
            changesFound = changesFound.asBoolean(),
        )
    }
}
