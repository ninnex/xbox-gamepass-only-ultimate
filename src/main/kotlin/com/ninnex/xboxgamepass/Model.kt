package com.ninnex.xboxgamepass

enum class Platform {
    CONSOLE,
    PC,
}

enum class PriceStatus {
    FREE,
    PAID,
    UNKNOWN,
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

data class ProductMetadata(
    val productId: String,
    val productTitle: String,
    val storePath: String,
    val priceStatus: PriceStatus,
)

data class GameRow(
    val name: String,
    val productId: String,
    val console: Boolean,
    val pc: Boolean,
    val cloud: Boolean,
    val storePath: String,
    val newSinceDate: String = "",
)

data class ProcessedGameRow(
    val name: String,
    val productId: String,
    val console: Boolean,
    val pc: Boolean,
    val cloud: Boolean,
    val category: String,
    val storePath: String,
    val newSinceDate: String = "",
)

data class Catalogs(
    val ultimate: List<GameRow>,
    val premium: List<GameRow>,
    val essential: List<GameRow>,
    val eaPlay: List<GameRow>,
    val ubisoftPlus: List<GameRow>,
)

data class ProcessedCatalogs(
    val ultimate: List<ProcessedGameRow>,
    val premium: List<ProcessedGameRow>,
    val essential: List<ProcessedGameRow>,
    val ultimateNoPremium: List<ProcessedGameRow>,
    val ultimateExclusive: List<ProcessedGameRow>,
)

data class GeneratedFile(
    val name: String,
    val content: String,
)

data class CatalogInfo(
    val xboxStoreBaseUrl: String,
    val newGameDisplayDays: Int,
    val lastCheckedAt: String,
    val changesFound: Boolean,
)

data class GenerationResult(
    val catalogs: Catalogs,
    val processed: ProcessedCatalogs,
    val catalogInfo: CatalogInfo,
    val files: List<GeneratedFile>,
) {
    val summary: Map<String, Int>
        get() = linkedMapOf(
            "ultimate.csv" to catalogs.ultimate.size,
            "premium.csv" to catalogs.premium.size,
            "essential.csv" to catalogs.essential.size,
            "ea-play.csv" to catalogs.eaPlay.size,
            "ubisoft-plus.csv" to catalogs.ubisoftPlus.size,
            "ultimate-no-premium.csv" to processed.ultimateNoPremium.size,
            "ultimate-exclusive.csv" to processed.ultimateExclusive.size,
        )
}
