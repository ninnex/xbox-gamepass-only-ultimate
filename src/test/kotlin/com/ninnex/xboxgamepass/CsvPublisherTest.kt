package com.ninnex.xboxgamepass

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CsvPublisherTest {
    @Test
    fun `publishes the complete validated set`() {
        val root = Files.createTempDirectory("publisher-test-")
        try {
            val output = root.resolve("data")
            val files = expectedFiles("New game")

            CsvPublisher.publish(output, files)

            assertEquals(AppConfig.expectedFileNames, Files.list(output).use { stream ->
                stream.map { it.fileName.toString() }.toList().toSet()
            })
            assertTrue(output.resolve("ultimate.csv").readText().contains("New game"))
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun `rejects an incomplete set before changing existing files`() {
        val root = Files.createTempDirectory("publisher-test-")
        try {
            val output = Files.createDirectories(root.resolve("data"))
            val existing = output.resolve("ultimate.csv")
            Files.writeString(existing, "previous", StandardCharsets.UTF_8)

            assertFailsWith<IllegalArgumentException> {
                CsvPublisher.publish(output, expectedFiles("new").dropLast(1))
            }

            assertEquals("previous", existing.readText())
        } finally {
            deleteRecursively(root)
        }
    }

    private fun expectedFiles(name: String): List<GeneratedFile> {
        val game = GameRow(name, "9TEST0000000", true, false, true, "game/9TEST0000000")
        val catalogs = Catalogs(
            ultimate = listOf(game),
            premium = listOf(game.copy(name = "Premium")),
            essential = listOf(game.copy(name = "Essential")),
            eaPlay = listOf(game.copy(name = "EA")),
            ubisoftPlus = listOf(game.copy(name = "Ubisoft")),
        )
        val csvFiles = CsvWriter.createFiles(
            catalogs,
            CatalogProcessor.buildProcessedRows(catalogs),
        )
        val info = CatalogInfo(
            AppConfig.XBOX_STORE_BASE_URL,
            AppConfig.NEW_GAME_DISPLAY_DAYS,
            Instant.parse("2026-07-25T12:00:00Z").toString(),
            changesFound = true,
        )
        return csvFiles + GeneratedFile("catalog-info.json", CatalogInfoWriter.write(info))
    }

    private fun deleteRecursively(root: java.nio.file.Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
