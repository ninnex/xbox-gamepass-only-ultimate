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
        productNames: Map<String, String>,
    ): List<GameRow> {
        val gamesByExactName = linkedMapOf<String, GameRow>()

        platformLists.forEach { platformList ->
            platformList.ids.forEach { id ->
                val name = productNames[id]
                    ?: throw IllegalStateException("No product name was found for $id.")
                val current = gamesByExactName[name] ?: GameRow(name, console = false, pc = false)
                gamesByExactName[name] = when (platformList.platform) {
                    Platform.CONSOLE -> current.copy(console = true)
                    Platform.PC -> current.copy(pc = true)
                }
            }
        }

        return gamesByExactName.values.sortedWith(compareBy(nameComparator) { it.name })
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
                ProcessedGameRow(game.name, game.console, game.pc, category)
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
            "eaPlay" to catalogs.eaPlay,
            "ubisoftPlus" to catalogs.ubisoftPlus,
        ).forEach { (catalogName, rows) ->
            require(rows.isNotEmpty()) { "$catalogName is empty." }
            require(rows.map { it.name }.toSet().size == rows.size) {
                "$catalogName contains duplicate exact names."
            }
            require(rows.none { it.name.isBlank() || (!it.console && !it.pc) }) {
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
                game.name.isNotBlank() &&
                (game.console || game.pc)
        }) { "ultimate-no-premium failed validation." }

        require(processed.ultimateExclusive.none {
            it.name in premiumNames || it.name in eaPlayNames || it.name in ubisoftPlusNames ||
                it.category != AppConfig.ULTIMATE_EXCLUSIVE ||
                it.name.isBlank() || (!it.console && !it.pc)
        }) { "ultimate-exclusive failed validation." }
        require(
            processed.ultimateExclusive == processed.ultimateNoPremium.filter {
                it.category == AppConfig.ULTIMATE_EXCLUSIVE
            },
        ) { "ultimate-exclusive does not match the classified Ultimate Exclusive rows." }
    }
}
