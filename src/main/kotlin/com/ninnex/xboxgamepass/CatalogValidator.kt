package com.ninnex.xboxgamepass

object CatalogValidator {
    fun validateFiles(files: List<GeneratedFile>) {
        require(files.size == AppConfig.expectedFileNames.size) {
            "Duplicate output file names are not allowed."
        }
        require(files.map { it.name }.toSet() == AppConfig.expectedFileNames) {
            "Exactly the seven CSV files and catalog-info.json are required."
        }
        files.filter { it.name.endsWith(".csv") }.forEach { validateCsv(it.name, it.content) }
        val info = files.single { it.name == "catalog-info.json" }
        CatalogInfoWriter.parse(info.content)
    }

    fun validateCsv(
        fileName: String,
        content: String,
        allowLegacyClassifiedFormat: Boolean = false,
    ) {
        require(fileName in AppConfig.expectedCsvFileNames) { "Unexpected CSV file: $fileName" }
        require(!content.startsWith('\uFEFF')) { "$fileName must not contain a UTF-8 BOM." }
        require('\r' !in content) { "$fileName must use LF line endings only." }
        require(content.endsWith('\n')) { "$fileName must end with LF." }

        val rows = CsvReader.parse(content)
        require(rows.size >= 2) { "$fileName is empty." }
        val classified = fileName in AppConfig.CLASSIFIED_CSV_FILE_NAMES
        val legacyClassified =
            classified &&
                allowLegacyClassifiedFormat &&
                rows.first() == CsvWriter.sourceHeaders
        val processed = classified && !legacyClassified
        val headers = if (processed) CsvWriter.processedHeaders else CsvWriter.sourceHeaders
        require(rows.first() == headers) { "$fileName has an invalid header." }

        val names = linkedSetOf<String>()
        val productIds = linkedSetOf<String>()
        rows.drop(1).forEachIndexed { index, row ->
            val rowNumber = index + 2
            require(row.size == headers.size) { "$fileName row $rowNumber has an invalid width." }
            val name = row[0]
            val productId = row[1]
            require(name.isNotBlank() && names.add(name)) {
                "$fileName row $rowNumber has a blank or duplicate name."
            }
            require(productId.matches(Regex("[A-Z0-9]{12}")) && productIds.add(productId)) {
                "$fileName row $rowNumber has an invalid or duplicate Product ID."
            }
            require(row[2] == "true" || row[2] == "false") {
                "$fileName row $rowNumber has an invalid console value."
            }
            require(row[3] == "true" || row[3] == "false") {
                "$fileName row $rowNumber has an invalid pc value."
            }
            require(row[2] == "true" || row[3] == "true") {
                "$fileName row $rowNumber has no supported platform."
            }

            val storePathIndex: Int
            val newSinceDateIndex: Int
            if (processed) {
                require(row[4] in AppConfig.ALLOWED_CATEGORIES) {
                    "$fileName row $rowNumber has an invalid category."
                }
                storePathIndex = 5
                newSinceDateIndex = 6
            } else {
                storePathIndex = 4
                newSinceDateIndex = 5
            }
            StorePath.validate(row[storePathIndex], productId)
            require(CatalogProcessor.isValidNewSinceDate(row[newSinceDateIndex])) {
                "$fileName row $rowNumber has an invalid newSinceDate."
            }
        }
    }
}
