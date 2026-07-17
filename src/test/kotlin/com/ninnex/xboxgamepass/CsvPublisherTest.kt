package com.ninnex.xboxgamepass

import java.nio.charset.StandardCharsets
import java.nio.file.Files
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
            val files = expectedFiles("new")

            CsvPublisher.publish(output, files)

            assertEquals(AppConfig.expectedFileNames, Files.list(output).use { stream ->
                stream.map { it.fileName.toString() }.toList().toSet()
            })
            assertTrue(output.resolve("ultimate.csv").readText().contains("new"))
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

    private fun expectedFiles(marker: String): List<GeneratedFile> =
        AppConfig.expectedFileNames.sorted().map { GeneratedFile(it, "\uFEFF$marker\r\n") }

    private fun deleteRecursively(root: java.nio.file.Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
