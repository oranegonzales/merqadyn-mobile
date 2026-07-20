package dev.merqadyn.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QueueRulesTest {
    @Test
    fun projectedStockIncludesExistingQueuedChanges() {
        assertEquals(13.5, QueueRules.projectedStock(10.0, 2.0, 1.5), 0.001)
    }

    @Test
    fun adjustmentCannotTakeProjectedStockBelowZero() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            QueueRules.validateAdjustment(serverOnHand = 5.0, alreadyQueued = -2.0, nextDelta = -4.0)
        }
        assertEquals("Stock cannot fall below zero.", error.message)
    }

    @Test
    fun zeroAdjustmentIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            QueueRules.validateAdjustment(5.0, 0.0, 0.0)
        }
    }

    @Test
    fun completeProductDraftIsAccepted() {
        QueueRules.validateProduct(ProductDraft("BREAD-01", "Hard dough bread", "Bakery", "loaf", 650.0))
    }

    @Test
    fun negativeProductPriceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            QueueRules.validateProduct(ProductDraft("BREAD-01", "Bread", "Bakery", "loaf", -1.0))
        }
    }
}
