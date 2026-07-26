package com.ninnex.xboxgamepass

import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class ProductPriceClassifierTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `classifies a zero list price and MSRP as free`() {
        assertEquals(
            PriceStatus.FREE,
            classify(sku(availability(displayRank = 0, listPrice = "0", msrp = "0.00"))),
        )
    }

    @Test
    fun `uses the primary paid availability instead of a secondary zero price`() {
        assertEquals(
            PriceStatus.PAID,
            classify(
                sku(
                    availability(displayRank = 0, listPrice = "24.99", msrp = "24.99"),
                    availability(displayRank = 1, listPrice = "0", msrp = "0"),
                ),
            ),
        )
    }

    @Test
    fun `uses the primary SKU instead of a lower priority zero price SKU`() {
        assertEquals(
            PriceStatus.PAID,
            classify(
                sku(
                    availability(displayRank = 0, listPrice = "24.99", msrp = "24.99"),
                    skuDisplayRank = 0,
                ),
                sku(
                    availability(displayRank = 0, listPrice = "0", msrp = "0"),
                    skuDisplayRank = 1,
                ),
            ),
        )
    }

    @Test
    fun `ignores trial SKUs`() {
        assertEquals(
            PriceStatus.PAID,
            classify(
                sku(
                    availability(displayRank = 0, listPrice = "0", msrp = "0"),
                    isTrial = true,
                ),
                sku(availability(displayRank = 1, listPrice = "19.99", msrp = "19.99")),
            ),
        )
    }

    @Test
    fun `returns unknown when only trial SKUs are available`() {
        assertEquals(
            PriceStatus.UNKNOWN,
            classify(
                sku(
                    availability(displayRank = 0, listPrice = "0", msrp = "0"),
                    isTrial = true,
                ),
            ),
        )
    }

    @Test
    fun `ignores availabilities that require remediation`() {
        assertEquals(
            PriceStatus.PAID,
            classify(
                sku(
                    availability(
                        displayRank = 0,
                        listPrice = "0",
                        msrp = "0",
                        remediationRequired = true,
                    ),
                    availability(displayRank = 1, listPrice = "9.99", msrp = "9.99"),
                ),
            ),
        )
    }

    @Test
    fun `does not treat a temporary zero list price as free`() {
        assertEquals(
            PriceStatus.PAID,
            classify(sku(availability(displayRank = 0, listPrice = "0", msrp = "29.99"))),
        )
    }

    @Test
    fun `returns unknown when a price is absent or invalid`() {
        assertEquals(
            PriceStatus.UNKNOWN,
            classify(sku(availability(displayRank = 0, listPrice = null, msrp = "10"))),
        )
        assertEquals(
            PriceStatus.UNKNOWN,
            classify(
                sku(
                    availability(
                        displayRank = 0,
                        listPrice = "\"not-a-price\"",
                        msrp = "10",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `selects the valid availability with the lowest display rank`() {
        assertEquals(
            PriceStatus.FREE,
            classify(
                sku(
                    availability(displayRank = 5, listPrice = "19.99", msrp = "19.99"),
                    availability(displayRank = 2, listPrice = "0", msrp = "0"),
                ),
            ),
        )
    }

    @Test
    fun `returns unknown for contradictory primary availabilities`() {
        assertEquals(
            PriceStatus.UNKNOWN,
            classify(
                sku(availability(displayRank = 0, listPrice = "0", msrp = "0")),
                sku(availability(displayRank = 0, listPrice = "14.99", msrp = "14.99")),
            ),
        )
    }

    private fun classify(vararg skus: String): PriceStatus {
        val node = objectMapper.readTree("[${skus.joinToString(",")}]")
        return ProductPriceClassifier.classify(node)
    }

    private fun sku(
        vararg availabilities: String,
        isTrial: Boolean = false,
        skuDisplayRank: Int = 0,
    ): String =
        """{"Sku":{"Properties":{"IsTrial":$isTrial,"SkuDisplayRank":$skuDisplayRank}},"Availabilities":[${
            availabilities.joinToString(",")
        }]}"""

    private fun availability(
        displayRank: Int,
        listPrice: String?,
        msrp: String?,
        remediationRequired: Boolean? = null,
    ): String {
        val fields = mutableListOf(""""DisplayRank":$displayRank""")
        remediationRequired?.let { fields += """"RemediationRequired":$it""" }
        val priceFields = buildList {
            listPrice?.let { add(""""ListPrice":$it""") }
            msrp?.let { add(""""MSRP":$it""") }
        }
        fields += """"OrderManagementData":{"Price":{${priceFields.joinToString(",")}}}"""
        return "{${fields.joinToString(",")}}"
    }
}
