package dev.merqadyn.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun find(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProductEntity)

    @Query("DELETE FROM products WHERE localOnly = 0")
    suspend fun clearServerProducts()
}

@Dao
interface InventoryDao {
    @Query("""
        SELECT i.*, COALESCE(SUM(CASE WHEN p.state IN ('QUEUED', 'UPLOADING') THEN p.stockDelta ELSE 0 END), 0) AS pendingDelta
        FROM inventory i
        LEFT JOIN pending_mutations p ON p.productId = i.productId AND p.locationId = i.locationId
        GROUP BY i.id
        ORDER BY i.locationName COLLATE NOCASE, i.productName COLLATE NOCASE
    """)
    fun observeWithPending(): Flow<List<InventoryWithPending>>

    @Query("SELECT * FROM inventory WHERE productId = :productId AND locationId = :locationId LIMIT 1")
    suspend fun find(productId: String, locationId: String): InventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<InventoryEntity>)

    @Query("DELETE FROM inventory")
    suspend fun clear()
}

@Dao
interface PendingMutationDao {
    @Query("SELECT * FROM pending_mutations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingMutationEntity>>

    @Query("SELECT * FROM pending_mutations WHERE state = 'QUEUED' ORDER BY createdAt LIMIT :limit")
    suspend fun queued(limit: Int): List<PendingMutationEntity>

    @Query("SELECT COUNT(*) FROM pending_mutations WHERE state IN ('QUEUED', 'UPLOADING')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(stockDelta), 0) FROM pending_mutations WHERE productId = :productId AND locationId = :locationId AND state IN ('QUEUED', 'UPLOADING')")
    suspend fun stockDelta(productId: String, locationId: String): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PendingMutationEntity)

    @Query("UPDATE pending_mutations SET state = :state, message = :message WHERE mutationId = :id")
    suspend fun setState(id: String, state: String, message: String?)

    @Query("UPDATE pending_mutations SET state = 'QUEUED' WHERE state = 'UPLOADING'")
    suspend fun recoverUploading()

    @Query("DELETE FROM pending_mutations WHERE mutationId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_mutations WHERE state = 'APPLIED'")
    suspend fun deleteApplied()
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun observe(): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun get(): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)
}
