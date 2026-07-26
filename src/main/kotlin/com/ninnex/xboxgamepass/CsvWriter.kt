package com.ninnex.xboxgamepass

object CsvWriter {
    val sourceHeaders = listOf("name", "productId", "console", "pc", "storePath", "newSinceDate")
    val processedHeaders = listOf(
        "name",
        "productId",
        "console",
        "pc",
        "category",
        "storePath",
        "newSinceDate",
    )

    fun escapeCsv(value: Any?): String {
        val text = when (value) {
            null -> ""
            is Boolean -> value.toString().lowercase()
            else -> value.toString()
        }
        return if (text.any { it == '"' || it == ',' || it == '\r' || it == '\n' }) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }

    fun sourceCsv(rows: List<GameRow>): String = toCsv(
        sourceHeaders,
        rows.map {
            listOf(
                it.name,
                it.productId,
                it.console,
                it.pc,
                it.storePath,
                it.newSinceDate,
            )
        },
    )

    fun processedCsv(rows: List<ProcessedGameRow>): String = toCsv(
        processedHeaders,
        rows.map {
            listOf(
                it.name,
                it.productId,
                it.console,
                it.pc,
                it.category,
                it.storePath,
                it.newSinceDate,
            )
        },
    )

    fun toCsv(headers: List<String>, rows: List<List<Any?>>): String {
        require(rows.all { it.size == headers.size }) { "CSV row width does not match its header." }
        val lines = buildList {
            add(headers.joinToString(","))
            rows.forEach { row -> add(row.joinToString(",") { escapeCsv(it) }) }
        }
        return "${lines.joinToString("\n")}\n"
    }

    fun createFiles(catalogs: Catalogs, processed: ProcessedCatalogs): List<GeneratedFile> = listOf(
        GeneratedFile("ultimate.csv", processedCsv(processed.ultimate)),
        GeneratedFile("premium.csv", processedCsv(processed.premium)),
        GeneratedFile("essential.csv", processedCsv(processed.essential)),
        GeneratedFile("ea-play.csv", sourceCsv(catalogs.eaPlay)),
        GeneratedFile("ubisoft-plus.csv", sourceCsv(catalogs.ubisoftPlus)),
        GeneratedFile("ultimate-no-premium.csv", processedCsv(processed.ultimateNoPremium)),
        GeneratedFile("ultimate-exclusive.csv", processedCsv(processed.ultimateExclusive)),
    )
}
