package com.ninnex.xboxgamepass

import com.ibm.icu.text.Collator
import com.ibm.icu.util.ULocale

object CatalogProcessor {
    private val englishLocale = ULocale.forLanguageTag("en-US")
    private val baseCollator = Collator.getInstance(englishLocale).apply {
        strength = Collator.PRIMARY
    }
    private val variantCollator = Collator.getInstance(englishLocale).apply {
        strength = Collator.TERTIARY
    }

    val nameComparator: Comparator<String> = Comparator { left, right ->
        baseCollator.compare(left, right).takeIf { it != 0 }
            ?: variantCollator.compare(left, right)
    }

    fun buildCatalogRows(
        platformLists: List<PlatformProductIds>,
        products: Map<String, ProductMetadata>,
    ): List<GameRow> {
        val gamesByExactName = linkedMapOf<String, ProductMembership>()

        platformLists.forEach { platformList ->
            platformList.ids.forEach { id ->
                val product = products[id]
                    ?: throw IllegalStateException("No product metadata was found for $id.")
                val membership = gamesByExactName.getOrPut(product.productTitle) {
                    ProductMembership()
                }
                when (platformList.platform) {
                    Platform.CONSOLE -> membership.consoleIds.add(id)
                    Platform.PC -> membership.pcIds.add(id)
                }
            }
        }

        return gamesByExactName.map { (name, membership) ->
            val selectedId = membership.consoleIds.firstOrNull { it in membership.pcIds }
                ?: membership.consoleIds.firstOrNull()
                ?: membership.pcIds.first()
            val selectedProduct = products.getValue(selectedId)
            GameRow(
                name = name,
                productId = selectedProduct.productId,
                console = membership.consoleIds.isNotEmpty(),
                pc = membership.pcIds.isNotEmpty(),
                storePath = selectedProduct.storePath,
            )
        }.sortedWith(compareBy(nameComparator) { it.name })
    }

    fun buildProcessedRows(catalogs: Catalogs): ProcessedCatalogs {
        val premiumNames = catalogs.premium.mapTo(hashSetOf()) { it.name }
        val eaPlayNames = catalogs.eaPlay.mapTo(hashSetOf()) { it.name }
        val ubisoftPlusNames = catalogs.ubisoftPlus.mapTo(hashSetOf()) { it.name }

        val ultimateNoPremium = catalogs.ultimate
            .asSequence()
            .filterNot { it.name in premiumNames }
            .map { game ->
                val category = when {
                    game.name in eaPlayNames -> AppConfig.EA_PLAY
                    game.name in ubisoftPlusNames -> AppConfig.UBISOFT_PLUS
                    else -> AppConfig.ULTIMATE_EXCLUSIVE
                }
                ProcessedGameRow(
                    name = game.name,
                    productId = game.productId,
                    console = game.console,
                    pc = game.pc,
                    category = category,
                    storePath = game.storePath,
                    newSinceDate = game.newSinceDate,
                )
            }
            .toList()

        return ProcessedCatalogs(
            ultimateNoPremium = ultimateNoPremium,
            ultimateExclusive = ultimateNoPremium.filter {
                it.category == AppConfig.ULTIMATE_EXCLUSIVE
            },
        )
    }

    fun validateRows(catalogs: Catalogs, processed: ProcessedCatalogs) {
        linkedMapOf(
            "ultimate" to catalogs.ultimate,
            "premium" to catalogs.premium,
            "essential" to catalogs.essential,
            "eaPlay" to catalogs.eaPlay,
            "ubisoftPlus" to catalogs.ubisoftPlus,
        ).forEach { (catalogName, rows) ->
            require(rows.isNotEmpty()) { "$catalogName is empty." }
            require(rows.map { it.name }.toSet().size == rows.size) {
                "$catalogName contains duplicate exact names."
            }
            require(rows.map { it.productId }.toSet().size == rows.size) {
                "$catalogName contains duplicate Product IDs."
            }
            require(rows.all(::isValidGameRow)) {
                "$catalogName contains an invalid row."
            }
        }

        require(processed.ultimateNoPremium.isNotEmpty()) { "ultimate-no-premium is empty." }
        require(processed.ultimateExclusive.isNotEmpty()) { "ultimate-exclusive is empty." }

        val premiumNames = catalogs.premium.mapTo(hashSetOf()) { it.name }
        val eaPlayNames = catalogs.eaPlay.mapTo(hashSetOf()) { it.name }
        val ubisoftPlusNames = catalogs.ubisoftPlus.mapTo(hashSetOf()) { it.name }
        val ultimateByName = catalogs.ultimate.associateBy { it.name }

        require(processed.ultimateNoPremium.map { it.name }.toSet().size == processed.ultimateNoPremium.size) {
            "ultimate-no-premium contains duplicate exact names."
        }
        require(processed.ultimateNoPremium.all { game ->
            val source = ultimateByName[game.name]
            val expectedCategory = when {
                game.name in eaPlayNames -> AppConfig.EA_PLAY
                game.name in ubisoftPlusNames -> AppConfig.UBISOFT_PLUS
                else -> AppConfig.ULTIMATE_EXCLUSIVE
            }
            source != null &&
                game.name !in premiumNames &&
                game.category in AppConfig.ALLOWED_CATEGORIES &&
                game.category == expectedCategory &&
                game.console == source.console &&
                game.pc == source.pc &&
                game.productId == source.productId &&
                game.storePath == source.storePath &&
                game.name.isNotBlank() &&
                (game.console || game.pc) &&
                isValidNewSinceDate(game.newSinceDate)
        }) { "ultimate-no-premium failed validation." }

        require(processed.ultimateExclusive.none {
                it.name in premiumNames || it.name in eaPlayNames || it.name in ubisoftPlusNames ||
                it.category != AppConfig.ULTIMATE_EXCLUSIVE ||
                it.name.isBlank() || (!it.console && !it.pc) ||
                !isValidNewSinceDate(it.newSinceDate)
        }) { "ultimate-exclusive failed validation." }
        require(
            processed.ultimateExclusive == processed.ultimateNoPremium.filter {
                it.category == AppConfig.ULTIMATE_EXCLUSIVE
            },
        ) { "ultimate-exclusive does not match the classified Ultimate Exclusive rows." }
    }

    fun isValidNewSinceDate(value: String): Boolean =
        value.isEmpty() || runCatching {
            val parsed = java.time.LocalDate.parse(value)
            parsed.toString() == value
        }.getOrDefault(false)

    private fun isValidGameRow(row: GameRow): Boolean =
        row.name.isNotBlank() &&
            (row.console || row.pc) &&
            row.productId.matches(Regex("[A-Z0-9]{12}")) &&
            runCatching { StorePath.validate(row.storePath, row.productId) }.isSuccess &&
            isValidNewSinceDate(row.newSinceDate)

    private data class ProductMembership(
        val consoleIds: LinkedHashSet<String> = linkedSetOf(),
        val pcIds: LinkedHashSet<String> = linkedSetOf(),
    )
}
