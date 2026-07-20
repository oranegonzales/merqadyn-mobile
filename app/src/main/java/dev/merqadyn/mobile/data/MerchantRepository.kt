package dev.merqadyn.mobile.data

import androidx.room.withTransaction
import dev.merqadyn.mobile.BuildConfig
import dev.merqadyn.mobile.data.local.InventoryEntity
import dev.merqadyn.mobile.data.local.MerqadynDatabase
import dev.merqadyn.mobile.data.local.PendingMutationEntity
import dev.merqadyn.mobile.data.local.ProductEntity
import dev.merqadyn.mobile.data.local.SyncStateEntity
import dev.merqadyn.mobile.data.remote.ApiFactory
import dev.merqadyn.mobile.data.remote.InventoryDto
import dev.merqadyn.mobile.data.remote.MutationDto
import dev.merqadyn.mobile.data.remote.ProductDto
import dev.merqadyn.mobile.data.remote.RedeemEnrollmentRequestDto
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
    private val credentials: CredentialStore,
    private val requestSync: () -> Unit,
) {
    private val products = db.productDao()
    private val inventory = db.inventoryDao()
    private val pending = db.pendingMutationDao()
    private val state = db.syncStateDao()
    private val json = Json { ignoreUnknownKeys = true }

    val connectionProfile = credentials.profile

    val snapshot: Flow<MerchantSnapshot> = combine(
        products.observeAll(),
        inventory.observeWithPending(),
        pending.observeAll(),
        state.observe(),
        pending.observePendingCount(),
    ) { productRows, inventoryRows, mutationRows, sync, count ->
        MerchantSnapshot(productRows, inventoryRows, mutationRows, sync ?: SyncStateEntity(), count)
    }

    suspend fun enroll(serverUrl: String, deviceId: String, code: String) {
        val endpoint = EndpointPolicy.normalize(serverUrl, BuildConfig.DEBUG)
        val normalizedDeviceId = UUID.fromString(deviceId.trim()).toString()
        val normalizedCode = code.trim().uppercase()
        require(normalizedCode.matches(Regex("^[23456789A-HJ-NP-Z]{10}$"))) { "Enter the 10-character enrollment code." }
        val response = ApiFactory.createEnrollment(endpoint).redeem(
            RedeemEnrollmentRequestDto(normalizedDeviceId, normalizedCode),
        )
        credentials.save(
            ConnectionProfile(
                serverUrl = endpoint,
                merchantId = response.merchantId,
                deviceId = response.deviceId,
                deviceToken = response.deviceToken,
            ),
        )
        resetLocalData()
        refresh()
    }

    suspend fun removeEnrollment(): Boolean {
        val profile = credentials.profile.value
        return try {
            if (profile != null) ApiFactory.create(profile).revoke(profile.merchantId, profile.deviceId)
            true
        } catch (_: Exception) {
            false
        } finally {
            credentials.clear()
            resetLocalData()
        }
    }

    suspend fun refresh() {
        val profile = credentials.profile.value ?: error("This phone has not been enrolled.")
        val current = state.get() ?: SyncStateEntity(merchantId = profile.merchantId)
        val api = ApiFactory.create(profile)
        try {
            val context = api.context(profile.merchantId)
            val remoteProducts = loadProducts(api = api, merchantId = profile.merchantId)
            val remoteInventory = loadInventory(api = api, merchantId = profile.merchantId)
            val now = Instant.now().toString()

            db.withTransaction {
                products.clearServerProducts()
                products.upsertAll(remoteProducts.map { it.toEntity() })
                inventory.clear()
                inventory.upsertAll(remoteInventory.map { it.toEntity() })
                state.upsert(
                    current.copy(
                        merchantId = profile.merchantId,
                        merchantName = context.merchant.name,
                        currency = context.merchant.currency,
                        cursor = context.latestCursor,
                        lastSyncAt = now,
                        lastError = null,
                    ),
                )
            }
        } catch (error: Exception) {
            state.upsert(current.copy(merchantId = profile.merchantId, lastError = error.userMessage()))
            throw error
        }
    }

    suspend fun queueStockAdjustment(productId: String, locationId: String, delta: Double, reason: String) {
        val row = inventory.find(productId, locationId) ?: error("Inventory record not found.")
        val alreadyQueued = pending.stockDelta(productId, locationId)
        QueueRules.validateAdjustment(row.serverOnHand, alreadyQueued, delta)
        val now = Instant.now().toString()
        pending.upsert(
            PendingMutationEntity(
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
            ),
        )
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
            products.upsert(existing.copy(name = draft.name.trim(), category = draft.category.trim(), unit = draft.unit.trim().lowercase(), price = draft.price, updatedAt = now))
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
        val profile = credentials.profile.value ?: return false
        pending.recoverUploading()
        val queued = pending.queued(100)
        if (queued.isEmpty()) {
            refresh()
            return false
        }
        val api = ApiFactory.create(profile)
        val syncState = state.get() ?: SyncStateEntity(merchantId = profile.merchantId)
        queued.forEach { pending.setState(it.mutationId, "UPLOADING", null) }

        return try {
            val response = api.sync(
                profile.merchantId,
                SyncBatchRequestDto(
                    deviceId = profile.deviceId,
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
            pending.queuedCount() > 0
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
    }

    private suspend fun resetLocalData() {
        db.withTransaction {
            products.clearAll()
            inventory.clear()
            pending.clearAll()
            state.clear()
        }
    }

    private suspend fun loadProducts(api: dev.merqadyn.mobile.data.remote.MerqadynApi, merchantId: String): List<ProductDto> {
        val result = mutableListOf<ProductDto>()
        var page = 0
        while (true) {
            check(page < MAX_PAGES) { "Product catalog exceeds this app version's safe refresh limit." }
            val response = api.productPage(merchantId, page)
            result += response.items
            if (!response.hasMore) break
            page += 1
        }
        return result
    }

    private suspend fun loadInventory(api: dev.merqadyn.mobile.data.remote.MerqadynApi, merchantId: String): List<InventoryDto> {
        val result = mutableListOf<InventoryDto>()
        var page = 0
        while (true) {
            check(page < MAX_PAGES) { "Inventory exceeds this app version's safe refresh limit." }
            val response = api.inventoryPage(merchantId, page)
            result += response.items
            if (!response.hasMore) break
            page += 1
        }
        return result
    }

    private fun ProductDto.toEntity() = ProductEntity(
        id = id,
        sku = sku,
        name = name,
        category = category,
        unit = unit,
        price = price.toDouble(),
        active = active,
        version = version,
        updatedAt = updatedAt,
    )

    private fun InventoryDto.toEntity() = InventoryEntity(
        id = id,
        locationId = locationId,
        locationCode = locationCode,
        locationName = locationName,
        productId = productId,
        sku = sku,
        productName = productName,
        serverOnHand = onHand.toDouble(),
        reserved = reserved.toDouble(),
        unit = unit,
        version = version,
        updatedAt = updatedAt,
    )

    private companion object {
        const val MAX_PAGES = 50
    }
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun Throwable.userMessage(): String = when (this) {
    is retrofit2.HttpException -> if (code() == 401 || code() == 403) "This phone's device access is invalid. Enroll it again." else "Server returned HTTP ${code()}."
    is java.net.ConnectException -> "Cannot reach the Merqadyn API."
    else -> message?.takeIf { it.isNotBlank() } ?: "Sync failed. Try again."
}
