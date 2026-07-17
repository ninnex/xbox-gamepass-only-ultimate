package com.ninnex.xboxgamepass

import java.nio.file.Path

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size <= 1) { "Usage: generator [output-directory]" }
        val outputDirectory = args.firstOrNull()?.let(Path::of) ?: Path.of("data")

        println("[Stage 1B-A] Starting Xbox Game Pass catalog generation.")
        val result = XboxGamePassGenerator().generate()
        CsvPublisher.publish(outputDirectory, result.files)
        result.summary.forEach { (fileName, rowCount) ->
            println("[Stage 1B-A] $fileName: $rowCount games")
        }
        println("[Stage 1B-A] Completed. Six CSV files were written to $outputDirectory.")
    }
}
