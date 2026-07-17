package com.ninnex.xboxgamepass

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Comparator

object CsvPublisher {
    fun publish(outputDirectory: Path, files: List<GeneratedFile>) {
        require(files.map { it.name }.toSet() == AppConfig.expectedFileNames) {
            "Exactly the six expected CSV files are required."
        }
        require(files.size == AppConfig.expectedFileNames.size) {
            "Duplicate output file names are not allowed."
        }

        val output = outputDirectory.toAbsolutePath().normalize()
        val parent = output.parent ?: throw IllegalArgumentException("The output directory needs a parent.")
        Files.createDirectories(parent)
        val staging = Files.createTempDirectory(parent, ".data-staging-")

        try {
            files.forEach { file ->
                val bytes = file.content.toByteArray(StandardCharsets.UTF_8)
                val stagedFile = staging.resolve(file.name)
                Files.write(stagedFile, bytes, StandardOpenOption.CREATE_NEW)
                check(Files.readAllBytes(stagedFile).contentEquals(bytes)) {
                    "Staged file ${file.name} failed byte validation."
                }
            }

            Files.createDirectories(output)
            val backups = files.associate { file ->
                val target = output.resolve(file.name)
                target to if (Files.exists(target)) Files.readAllBytes(target) else null
            }

            try {
                files.forEach { file ->
                    moveReplacing(staging.resolve(file.name), output.resolve(file.name))
                }
            } catch (error: Exception) {
                backups.forEach { (target, previousBytes) ->
                    runCatching {
                        if (previousBytes == null) {
                            Files.deleteIfExists(target)
                        } else {
                            Files.write(
                                target,
                                previousBytes,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                            )
                        }
                    }.onFailure(error::addSuppressed)
                }
                throw error
            }
        } finally {
            deleteRecursively(staging)
        }
    }

    private fun moveReplacing(source: Path, target: Path) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deleteRecursively(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
