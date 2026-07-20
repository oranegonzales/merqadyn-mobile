package dev.merqadyn.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProductEntity::class, InventoryEntity::class, PendingMutationEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MerqadynDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun pendingMutationDao(): PendingMutationDao
    abstract fun syncStateDao(): SyncStateDao
}
