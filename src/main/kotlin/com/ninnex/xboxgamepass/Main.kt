package com.ninnex.xboxgamepass

import java.nio.file.Path

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size <= 2) {
            "Usage: generator [output-directory] [published-baseline-directory]"
        }
        val outputDirectory = args.firstOrNull()?.let(Path::of) ?: Path.of("data")
        val baselineDirectory = args.getOrNull(1)?.let(Path::of) ?: outputDirectory

        println("[Phase B] Starting Xbox Game Pass catalog generation.")
        val result = XboxGamePassGenerator().generate(baselineDirectory)
        CsvPublisher.publish(outputDirectory, result.files)
        result.summary.forEach { (fileName, rowCount) ->
            println("[Phase B] $fileName: $rowCount games")
        }
        println(
            "[Phase B] Completed. Seven CSV files and catalog-info.json were written to " +
                "$outputDirectory (changesFound=${result.catalogInfo.changesFound}).",
        )
    }
}
