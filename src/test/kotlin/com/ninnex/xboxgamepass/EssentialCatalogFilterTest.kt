package com.ninnex.xboxgamepass

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EssentialCatalogFilterTest {
    @Test
    fun `filters each Product ID before combining platforms`() {
        val consoleId = "9CONSFREE001"
        val pcId = "9PCPAID00001"
        val products = mapOf(
            consoleId to product(consoleId, "Same game", PriceStatus.FREE),
            pcId to product(pcId, "Same game", PriceStatus.PAID),
        )

        val filtered = EssentialCatalogFilter.filter(
            listOf(
                PlatformProductIds(Platform.CONSOLE, listOf(consoleId)),
                PlatformProductIds(Platform.PC, listOf(pcId)),
            ),
            products,
        )
        val rows = CatalogProcessor.buildCatalogRows(filtered, products, emptySet())

        assertEquals(listOf(pcId), filtered.single { it.platform == Platform.PC }.ids)
        assertTrue(filtered.single { it.platform == Platform.CONSOLE }.ids.isEmpty())
        assertEquals(pcId, rows.single().productId)
        assertFalse(rows.single().console)
        assertTrue(rows.single().pc)
    }

    @Test
    fun `fails with all unknown Essential Product IDs`() {
        val firstId = "9UNKNOWN0001"
        val secondId = "9UNKNOWN0002"
        val error = assertFailsWith<IllegalStateException> {
            EssentialCatalogFilter.filter(
                listOf(
                    PlatformProductIds(Platform.CONSOLE, listOf(secondId, firstId)),
                ),
                mapOf(
                    firstId to product(firstId, "First", PriceStatus.UNKNOWN),
                    secondId to product(secondId, "Second", PriceStatus.UNKNOWN),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("$firstId, $secondId"))
        assertTrue(error.message.orEmpty().contains("No catalog files were written"))
    }

    @Test
    fun `generator removes free games only from Essential and accepts unknown prices elsewhere`() {
        val root = Files.createTempDirectory("essential-filter-generator-")
        try {
            val client = FakeCatalogClient()
            val result = XboxGamePassGenerator(
                client = client,
                clock = Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC),
            ).generate(root.resolve("baseline"))

            assertEquals(1, client.cloudCalls)
            assertEquals(listOf(FakeCatalogClient.ESSENTIAL_PAID_ID), result.catalogs.essential.map {
                it.productId
            })
            assertTrue(result.catalogs.ultimate.any {
                it.productId == FakeCatalogClient.SHARED_FREE_ID
            })
            assertTrue(result.catalogs.ubisoftPlus.any {
                it.productId == FakeCatalogClient.UBISOFT_UNKNOWN_ID
            })
            assertFalse(
                result.files.single { it.name == "essential.csv" }
                    .content.contains("Free shared"),
            )
            assertTrue(
                result.files.single { it.name == "ultimate.csv" }
                    .content.contains("Free shared"),
            )
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun `unknown Essential price aborts generation before files are changed`() {
        val root = Files.createTempDirectory("essential-filter-unknown-")
        try {
            val output = Files.createDirectories(root.resolve("published"))
            val marker = output.resolve("unchanged.txt")
            Files.writeString(marker, "keep")
            val error = assertFailsWith<IllegalStateException> {
                XboxGamePassGenerator(
                    client = FakeCatalogClient(PriceStatus.UNKNOWN),
                ).generate(output)
            }

            assertTrue(error.message.orEmpty().contains(FakeCatalogClient.ESSENTIAL_PAID_ID))
            assertEquals("keep", Files.readString(marker))
            assertEquals(setOf("unchanged.txt"), Files.list(output).use { paths ->
                paths.map { it.fileName.toString() }.toList().toSet()
            })
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun `Cloud failure aborts generation before files are changed`() {
        val root = Files.createTempDirectory("cloud-failure-")
        try {
            val output = Files.createDirectories(root.resolve("published"))
            val marker = output.resolve("unchanged.txt")
            Files.writeString(marker, "keep")

            assertFailsWith<IllegalStateException> {
                XboxGamePassGenerator(
                    client = FakeCatalogClient(failCloud = true),
                ).generate(output)
            }

            assertEquals("keep", Files.readString(marker))
            assertEquals(setOf("unchanged.txt"), Files.list(output).use { paths ->
                paths.map { it.fileName.toString() }.toList().toSet()
            })
        } finally {
            deleteRecursively(root)
        }
    }

    private fun product(id: String, title: String, status: PriceStatus) =
        ProductMetadata(id, title, StorePath.fromProductId(id), status)

    private fun deleteRecursively(root: java.nio.file.Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private class FakeCatalogClient(
        private val essentialPaidStatus: PriceStatus = PriceStatus.PAID,
        private val failCloud: Boolean = false,
    ) : XboxCatalogClient {
        var cloudCalls = 0
            private set

        override fun loadSigl(
            source: CatalogSource,
            catalogName: String,
        ): PlatformProductIds {
            val ids = when (catalogName) {
                "ultimate" -> listOf(ULTIMATE_ID, SHARED_FREE_ID)
                "premium" -> listOf(PREMIUM_ID)
                "essential" -> if (source.platform == Platform.CONSOLE) {
                    listOf(SHARED_FREE_ID, ESSENTIAL_PAID_ID)
                } else {
                    listOf(ESSENTIAL_PAID_ID)
                }
                "ea-play" -> listOf(EA_PLAY_ID)
                "ubisoft-plus" -> listOf(UBISOFT_UNKNOWN_ID)
                else -> error("Unexpected catalog $catalogName")
            }
            return PlatformProductIds(source.platform, ids)
        }

        override fun loadCloudProductIds(): Set<String> {
            cloudCalls += 1
            check(!failCloud) { "Cloud unavailable" }
            return setOf(ULTIMATE_ID, ESSENTIAL_PAID_ID)
        }

        override fun loadProducts(productIds: List<String>): Map<String, ProductMetadata> {
            val products = listOf(
                product(ULTIMATE_ID, "Ultimate only", PriceStatus.PAID),
                product(SHARED_FREE_ID, "Free shared", PriceStatus.FREE),
                product(PREMIUM_ID, "Premium only", PriceStatus.PAID),
                product(ESSENTIAL_PAID_ID, "Essential paid", essentialPaidStatus),
                product(EA_PLAY_ID, "EA Play only", PriceStatus.PAID),
                product(UBISOFT_UNKNOWN_ID, "Ubisoft unknown", PriceStatus.UNKNOWN),
            )
            return products.filter { it.productId in productIds }.associateBy { it.productId }
        }

        private fun product(id: String, title: String, status: PriceStatus) =
            ProductMetadata(id, title, StorePath.fromProductId(id), status)

        companion object {
            const val ULTIMATE_ID = "9ULTIMATE001"
            const val SHARED_FREE_ID = "9SHAREFREE01"
            const val PREMIUM_ID = "9PREMIUM0001"
            const val ESSENTIAL_PAID_ID = "9ESSPAID0001"
            const val EA_PLAY_ID = "9EAPLAY00001"
            const val UBISOFT_UNKNOWN_ID = "9UBISOFT0001"
        }
    }
}
