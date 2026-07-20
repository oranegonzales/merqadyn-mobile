package dev.merqadyn.mobile.data

import androidx.room.withTransaction
import dev.merqadyn.mobile.BuildConfig
import dev.merqadyn.mobile.data.local.InventoryEntity
import dev.merqadyn.mobile.data.local.MerqadynDatabase
import dev.merqadyn.mobile.data.local.PendingMutationEntity
import dev.merqadyn.mobile.data.local.ProductEntity
import dev.merqadyn.mobile.data.local.SyncStateEntity
import dev.merqadyn.mobile.data.remote.MerqadynApi
import dev.merqadyn.mobile.data.remote.MutationDto
import dev.merqadyn.mobile.data.remote.SyncBatchRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.util.UUID

data class MerchantSnapshot(
    val products: List<ProductEntity> = emptyList(),
    val inventory: List<dev.merqadyn.mobile.data.local.InventoryWithPending> = emptyList(),
    val mutations: List<PendingMutationEntity> = emptyList(),
    val syncState: SyncStateEntity = SyncStateEntity(),
    val pendingCount: Int = 0,
)

data class ProductDraft(
    val sku: String,
    val name: String,
    val category: String,
    val unit: String,
    val price: Double,
)

class MerchantRepository(
    private val db: MerqadynDatabase,
    private val api: MerqadynApi,
    private val requestSync: () -> Unit,
) {
    private val products = db.productDao()
    private val inventory = db.inventoryDao()
    private val pending = db.pendingMutationDao()
    private val state = db.syncStateDao()
    private val json = Json { ignoreUnknownKeys = true }

    val snapshot: Flow<MerchantSnapshot> = combine(
        products.observeAll(),
        inventory.observeWithPending(),
        pending.observeAll(),
        state.observe(),
        pending.observePendingCount(),
    ) { productRows, inventoryRows, mutationRows, sync, count ->
        MerchantSnapshot(productRows, inventoryRows, mutationRows, sync ?: SyncStateEntity(), count)
    }

    suspend fun refresh() {
        val current = state.get() ?: SyncStateEntity()
        try {
            val merchantId = current.merchantId ?: api.config().demoMerchantId
            val overview = api.overview(merchantId)
            val remoteProducts = api.products(merchantId)
            val remoteInventory = api.inventory(merchantId)
            val now = Instant.now().toString()

            db.withTransaction {
                products.clearServerProducts()
                products.upsertAll(remoteProducts.map {
                    ProductEntity(
                        id = it.id,
                        sku = it.sku,
                        name = it.name,
                        category = it.category,
                        unit = it.unit,
                        price = it.price.toDouble(),
                        active = it.active,
                        version = it.version,
                        updatedAt = it.updatedAt,
                    )
                })
                inventory.clear()
                inventory.upsertAll(remoteInventory.map {
                    InventoryEntity(
                        id = it.id,
                        locationId = it.locationId,
                        locationCode = it.locationCode,
                        locationName = it.locationName,
                        productId = it.productId,
                        sku = it.sku,
                        productName = it.productName,
                        serverOnHand = it.onHand.toDouble(),
                        reserved = it.reserved.toDouble(),
                        unit = it.unit,
                        version = it.version,
                        updatedAt = it.updatedAt,
                    )
                })
                state.upsert(
                    current.copy(
                        merchantId = merchantId,
                        merchantName = overview.merchant.name,
                        currency = overview.merchant.currency,
                        cursor = overview.latestCursor,
                        lastSyncAt = now,
                        lastError = null,
                    ),
                )
            }
        } catch (error: Exception) {
            state.upsert(current.copy(lastError = error.userMessage()))
            throw error
        }
    }

    suspend fun queueStockAdjustment(productId: String, locationId: String, delta: Double, reason: String) {
        val row = inventory.find(productId, locationId) ?: error("Inventory record not found.")
        val alreadyQueued = pending.stockDelta(productId, locationId)
        QueueRules.validateAdjustment(row.serverOnHand, alreadyQueued, delta)
        val now = Instant.now().toString()
        val mutation = PendingMutationEntity(
            mutationId = UUID.randomUUID().toString(),
            type = "ADJUST_STOCK",
            entityId = productId,
            baseVersion = null,
            payloadJson = JsonObject(
                mapOf(
                    "locationId" to JsonPrimitive(locationId),
                    "delta" to JsonPrimitive(delta),
                    "reason" to JsonPrimitive(reason.trim().ifBlank { "Stock count correction" }),
                ),
            ).toString(),
            productId = productId,
            locationId = locationId,
            stockDelta = delta,
            summary = "${if (delta > 0) "+" else ""}${delta.clean()} ${row.unit} · ${row.productName}",
            state = "QUEUED",
            message = null,
            createdAt = now,
        )
        pending.upsert(mutation)
        requestSync()
    }

    suspend fun queueProduct(draft: ProductDraft) {
        QueueRules.validateProduct(draft)
        val productId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val payload = JsonObject(
            mapOf(
                "sku" to JsonPrimitive(draft.sku.trim().uppercase()),
                "name" to JsonPrimitive(draft.name.trim()),
                "category" to JsonPrimitive(draft.category.trim()),
                "unit" to JsonPrimitive(draft.unit.trim().lowercase()),
                "price" to JsonPrimitive(draft.price),
            ),
        )
        db.withTransaction {
            products.upsert(
                ProductEntity(
                    id = productId,
                    sku = draft.sku.trim().uppercase(),
                    name = draft.name.trim(),
                    category = draft.category.trim(),
                    unit = draft.unit.trim().lowercase(),
                    price = draft.price,
                    active = true,
                    version = 0,
                    updatedAt = now,
                    localOnly = true,
                ),
            )
            pending.upsert(
                PendingMutationEntity(
                    mutationId = UUID.randomUUID().toString(),
                    type = "CREATE_PRODUCT",
                    entityId = productId,
                    baseVersion = null,
                    payloadJson = payload.toString(),
                    productId = productId,
                    locationId = null,
                    stockDelta = 0.0,
                    summary = "Create ${draft.name.trim()}",
                    state = "QUEUED",
                    message = null,
                    createdAt = now,
                ),
            )
        }
        requestSync()
    }

    suspend fun queueProductUpdate(productId: String, draft: ProductDraft) {
        QueueRules.validateProduct(draft)
        val existing = products.find(productId) ?: error("Product not found.")
        require(!existing.localOnly) { "Wait for this new product to sync before editing it." }
        val now = Instant.now().toString()
        val payload = JsonObject(
            mapOf(
                "name" to JsonPrimitive(draft.name.trim()),
                "category" to JsonPrimitive(draft.category.trim()),
                "unit" to JsonPrimitive(draft.unit.trim().lowercase()),
                "price" to JsonPrimitive(draft.price),
            ),
        )
        db.withTransaction {
            products.upsert(
                existing.copy(
                    name = draft.name.trim(),
                    category = draft.category.trim(),
                    unit = draft.unit.trim().lowercase(),
                    price = draft.price,
                    updatedAt = now,
                ),
            )
            pending.upsert(
                PendingMutationEntity(
                    mutationId = UUID.randomUUID().toString(),
                    type = "UPDATE_PRODUCT",
                    entityId = productId,
                    baseVersion = existing.version,
                    payloadJson = payload.toString(),
                    productId = productId,
                    locationId = null,
                    stockDelta = 0.0,
                    summary = "Update ${draft.name.trim()}",
                    state = "QUEUED",
                    message = null,
                    createdAt = now,
                ),
            )
        }
        requestSync()
    }

    suspend fun sync(): Boolean {
        pending.recoverUploading()
        val queued = pending.queued(100)
        if (queued.isEmpty()) {
            refresh()
            return true
        }
        check(BuildConfig.ADMIN_USER.isNotBlank() && BuildConfig.ADMIN_PASSWORD.isNotBlank()) {
            "Admin credentials are missing. Run scripts/configure-local.ps1."
        }
        var syncState = state.get() ?: SyncStateEntity()
        val merchantId = syncState.merchantId ?: api.config().demoMerchantId.also {
            syncState = syncState.copy(merchantId = it)
            state.upsert(syncState)
        }
        queued.forEach { pending.setState(it.mutationId, "UPLOADING", null) }

        return try {
            val response = api.sync(
                merchantId,
                SyncBatchRequestDto(
                    deviceId = BuildConfig.DEVICE_ID,
                    lastPulledCursor = syncState.cursor,
                    mutations = queued.map { row ->
                        MutationDto(
                            mutationId = row.mutationId,
                            type = row.type,
                            entityId = row.entityId,
                            baseVersion = row.baseVersion,
                            payload = json.parseToJsonElement(row.payloadJson) as JsonObject,
                            occurredAt = row.createdAt,
                        )
                    },
                ),
            )
            db.withTransaction {
                response.results.forEach { result ->
                    when (result.status) {
                        "APPLIED" -> pending.delete(result.mutationId)
                        "CONFLICT" -> pending.setState(result.mutationId, "CONFLICT", result.message)
                        else -> pending.setState(result.mutationId, "REJECTED", result.message)
                    }
                }
                state.upsert(syncState.copy(cursor = response.nextCursor, lastSyncAt = response.completedAt, lastError = null))
            }
            refresh()
            true
        } catch (error: Exception) {
            queued.forEach { pending.setState(it.mutationId, "QUEUED", null) }
            state.upsert(syncState.copy(lastError = error.userMessage()))
            throw error
        }
    }

    suspend fun retryMutation(id: String) {
        pending.setState(id, "QUEUED", null)
        requestSync()
    }

    suspend fun dismissMutation(id: String) {
        pending.delete(id)
        refresh()
    }

}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun Throwable.userMessage(): String = when (this) {
    is retrofit2.HttpException -> "Server returned HTTP ${code()}."
    is java.net.ConnectException -> "Cannot reach the Merqadyn API."
    else -> message?.takeIf { it.isNotBlank() } ?: "Sync failed. Try again."
}
