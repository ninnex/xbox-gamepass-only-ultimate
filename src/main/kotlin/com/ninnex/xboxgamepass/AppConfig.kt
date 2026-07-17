package com.ninnex.xboxgamepass

import java.time.Duration

object AppConfig {
    const val MARKET = "US"
    const val LANGUAGE = "en-us"
    const val SIGL_ENDPOINT = "https://catalog.gamepass.com/sigls/v3"
    const val PRODUCTS_ENDPOINT = "https://displaycatalog.mp.microsoft.com/v7.0/products"
    const val MS_CV = "DGU1mcuYo0WMMp+F.1"
    const val PRODUCT_BATCH_SIZE = 20
    const val PRODUCT_CONCURRENCY = 4
    const val REQUEST_ATTEMPTS = 3
    val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(60)

    const val EA_PLAY = "EA Play"
    const val UBISOFT_PLUS = "Ubisoft+ Classics"
    const val ULTIMATE_EXCLUSIVE = "Ultimate Exclusive"
    val ALLOWED_CATEGORIES = setOf(EA_PLAY, UBISOFT_PLUS, ULTIMATE_EXCLUSIVE)

    val catalogs = listOf(
        CatalogDefinition(
            key = "ultimate",
            fileName = "ultimate.csv",
            sources = listOf(
                source(
                    id = "97c6c862-d28a-4907-a3d5-c401f2296a53",
                    platform = Platform.CONSOLE,
                    platformContext = "ConsoleGen8;ConsoleGen9",
                    subscriptionContext = "cfq7ttc0khs0",
                ),
                source(
                    id = "97c6c862-d28a-4907-a3d5-c401f2296a53",
                    platform = Platform.PC,
                    platformContext = "pc",
                    subscriptionContext = "cfq7ttc0khs0",
                ),
            ),
        ),
        CatalogDefinition(
            key = "premium",
            fileName = "premium.csv",
            sources = listOf(
                source(
                    id = "09a72c0d-c466-426a-9580-b78955d8173a",
                    platform = Platform.CONSOLE,
                    platformContext = "ConsoleGen8;ConsoleGen9",
                    subscriptionContext = "cfq7ttc0p85b",
                ),
                source(
                    id = "09a72c0d-c466-426a-9580-b78955d8173a",
                    platform = Platform.PC,
                    platformContext = "pc",
                    subscriptionContext = "cfq7ttc0p85b",
                ),
            ),
        ),
        CatalogDefinition(
            key = "eaPlay",
            fileName = "ea-play.csv",
            sources = listOf(
                source(
                    id = "b8900d09-a491-44cc-916e-32b5acae621b",
                    platform = Platform.CONSOLE,
                    platformContext = "ConsoleGen8;ConsoleGen9",
                    subscriptionContext = "cfq7ttc0khs0",
                ),
                source(
                    id = "1d33fbb9-b895-4732-a8ca-a55c8b99fa2c",
                    platform = Platform.PC,
                    platformContext = "pc",
                    subscriptionContext = "cfq7ttc0khs0",
                ),
            ),
        ),
        CatalogDefinition(
            key = "ubisoftPlus",
            fileName = "ubisoft-plus.csv",
            sources = listOf(
                source(
                    id = "66ec875c-a391-44f5-9a54-a28bd6f976ce",
                    platform = Platform.CONSOLE,
                    platformContext = "ConsoleGen8;ConsoleGen9",
                    subscriptionContext = "cfq7ttc0khs0",
                ),
                source(
                    id = "66ec875c-a391-44f5-9a54-a28bd6f976ce",
                    platform = Platform.PC,
                    platformContext = "pc",
                    subscriptionContext = "cfq7ttc0khs0",
                ),
            ),
        ),
    )

    val expectedFileNames = catalogs.map { it.fileName }.toSet() +
        setOf("ultimate-no-premium.csv", "ultimate-exclusive.csv")

    private fun source(
        id: String,
        platform: Platform,
        platformContext: String,
        subscriptionContext: String,
    ) = CatalogSource(id, platform, platformContext, subscriptionContext)
}
