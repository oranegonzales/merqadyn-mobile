package dev.merqadyn.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val sku: String,
    val name: String,
    val category: String,
    val unit: String,
    val price: Double,
    val active: Boolean,
    val version: Long,
    val updatedAt: String,
    val localOnly: Boolean = false,
)

@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey val id: String,
    val locationId: String,
    val locationCode: String,
    val locationName: String,
    val productId: String,
    val sku: String,
    val productName: String,
    val serverOnHand: Double,
    val reserved: Double,
    val unit: String,
    val version: Long,
    val updatedAt: String,
)

@Entity(tableName = "pending_mutations")
data class PendingMutationEntity(
    @PrimaryKey val mutationId: String,
    val type: String,
    val entityId: String?,
    val baseVersion: Long?,
    val payloadJson: String,
    val productId: String?,
    val locationId: String?,
    val stockDelta: Double,
    val summary: String,
    val state: String,
    val message: String?,
    val createdAt: String,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val merchantId: String? = null,
    val merchantName: String? = null,
    val currency: String = "JMD",
    val cursor: Long = 0,
    val lastSyncAt: String? = null,
    val lastError: String? = null,
)

data class InventoryWithPending(
    val id: String,
    val locationId: String,
    val locationCode: String,
    val locationName: String,
    val productId: String,
    val sku: String,
    val productName: String,
    val serverOnHand: Double,
    val reserved: Double,
    val unit: String,
    val version: Long,
    val updatedAt: String,
    val pendingDelta: Double,
) {
    val onHand: Double get() = serverOnHand + pendingDelta
    val available: Double get() = onHand - reserved
}
