package dev.merqadyn.mobile.data

object QueueRules {
    fun projectedStock(serverOnHand: Double, alreadyQueued: Double, nextDelta: Double): Double =
        serverOnHand + alreadyQueued + nextDelta

    fun validateAdjustment(serverOnHand: Double, alreadyQueued: Double, nextDelta: Double) {
        require(nextDelta != 0.0) { "Enter a quantity other than zero." }
        require(projectedStock(serverOnHand, alreadyQueued, nextDelta) >= 0) { "Stock cannot fall below zero." }
    }

    fun validateProduct(draft: ProductDraft) {
        require(draft.sku.isNotBlank()) { "SKU is required." }
        require(draft.name.isNotBlank()) { "Product name is required." }
        require(draft.category.isNotBlank()) { "Category is required." }
        require(draft.unit.isNotBlank()) { "Unit is required." }
        require(draft.price >= 0) { "Price cannot be negative." }
    }
}
