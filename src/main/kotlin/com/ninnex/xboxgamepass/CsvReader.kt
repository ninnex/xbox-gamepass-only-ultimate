package com.ninnex.xboxgamepass

object CsvReader {
    fun parse(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0

        while (index < content.length) {
            val character = content[index]
            if (quoted) {
                when {
                    character == '"' && content.getOrNull(index + 1) == '"' -> {
                        field.append('"')
                        index += 1
                    }
                    character == '"' -> quoted = false
                    else -> field.append(character)
                }
            } else {
                when (character) {
                    '"' -> quoted = true
                    ',' -> {
                        row.add(field.toString())
                        field.clear()
                    }
                    '\n' -> {
                        row.add(field.toString())
                        field.clear()
                        if (row.any(String::isNotEmpty)) rows.add(row)
                        row = mutableListOf()
                    }
                    else -> field.append(character)
                }
            }
            index += 1
        }

        require(!quoted) { "CSV contains an unterminated quoted field." }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
