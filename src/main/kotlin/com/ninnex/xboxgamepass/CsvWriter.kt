package com.ninnex.xboxgamepass

object CsvWriter {
    private val sourceHeaders = listOf("name", "console", "pc")
    private val processedHeaders = listOf("name", "console", "pc", "category")

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
        rows.map { listOf(it.name, it.console, it.pc) },
    )

    fun processedCsv(rows: List<ProcessedGameRow>): String = toCsv(
        processedHeaders,
        rows.map { listOf(it.name, it.console, it.pc, it.category) },
    )

    fun toCsv(headers: List<String>, rows: List<List<Any?>>): String {
        require(rows.all { it.size == headers.size }) { "CSV row width does not match its header." }
        val lines = buildList {
            add(headers.joinToString(","))
            rows.forEach { row -> add(row.joinToString(",") { escapeCsv(it) }) }
        }
        return "\uFEFF${lines.joinToString("\r\n")}\r\n"
    }

    fun createFiles(catalogs: Catalogs, processed: ProcessedCatalogs): List<GeneratedFile> = listOf(
        GeneratedFile("ultimate.csv", sourceCsv(catalogs.ultimate)),
        GeneratedFile("premium.csv", sourceCsv(catalogs.premium)),
        GeneratedFile("ea-play.csv", sourceCsv(catalogs.eaPlay)),
        GeneratedFile("ubisoft-plus.csv", sourceCsv(catalogs.ubisoftPlus)),
        GeneratedFile("ultimate-no-premium.csv", processedCsv(processed.ultimateNoPremium)),
        GeneratedFile("ultimate-exclusive.csv", processedCsv(processed.ultimateExclusive)),
    )
}
