package com.ninnex.xboxgamepass

enum class Platform {
    CONSOLE,
    PC,
}

data class CatalogSource(
    val id: String,
    val platform: Platform,
    val platformContext: String,
    val subscriptionContext: String,
)

data class CatalogDefinition(
    val key: String,
    val fileName: String,
    val sources: List<CatalogSource>,
)

data class PlatformProductIds(
    val platform: Platform,
    val ids: List<String>,
)

data class GameRow(
    val name: String,
    val console: Boolean,
    val pc: Boolean,
)

data class ProcessedGameRow(
    val name: String,
    val console: Boolean,
    val pc: Boolean,
    val category: String,
)

data class Catalogs(
    val ultimate: List<GameRow>,
    val premium: List<GameRow>,
    val eaPlay: List<GameRow>,
    val ubisoftPlus: List<GameRow>,
)

data class ProcessedCatalogs(
    val ultimateNoPremium: List<ProcessedGameRow>,
    val ultimateExclusive: List<ProcessedGameRow>,
)

data class GeneratedFile(
    val name: String,
    val content: String,
)

data class GenerationResult(
    val catalogs: Catalogs,
    val processed: ProcessedCatalogs,
    val files: List<GeneratedFile>,
) {
    val summary: Map<String, Int>
        get() = linkedMapOf(
            "ultimate.csv" to catalogs.ultimate.size,
            "premium.csv" to catalogs.premium.size,
            "ea-play.csv" to catalogs.eaPlay.size,
            "ubisoft-plus.csv" to catalogs.ubisoftPlus.size,
            "ultimate-no-premium.csv" to processed.ultimateNoPremium.size,
            "ultimate-exclusive.csv" to processed.ultimateExclusive.size,
        )
}
