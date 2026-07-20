package dev.merqadyn.mobile.data

import dev.merqadyn.mobile.data.remote.InventoryDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class BigDecimalSerializerTest {
    @Test
    fun apiNumbersDecodeWithoutPrecisionLoss() {
        val dto = Json.decodeFromString<InventoryDto>(
            """{"id":"1","locationId":"2","locationCode":"HWT","locationName":"Half-Way Tree","productId":"3","sku":"BREAD-01","productName":"Bread","onHand":12.375,"reserved":1.000,"available":11.375,"unit":"loaf","version":4,"updatedAt":"2026-07-20T12:00:00Z"}""",
        )

        assertEquals(BigDecimal("12.375"), dto.onHand)
        assertEquals(BigDecimal("1.000"), dto.reserved)
    }
}
