package com.sandbox.patchsim.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetAppDao {
    @Query("SELECT * FROM target_apps ORDER BY isDemo DESC, label ASC")
    fun observeAll(): Flow<List<TargetAppEntity>>

    @Query("SELECT * FROM target_apps WHERE packageName = :pkg")
    suspend fun find(pkg: String): TargetAppEntity?

    @Upsert
    suspend fun upsert(app: TargetAppEntity)

    @Delete
    suspend fun delete(app: TargetAppEntity)

    @Query("SELECT COUNT(*) FROM target_apps")
    suspend fun count(): Int
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE packageName = :pkg")
    fun observe(pkg: String): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE packageName = :pkg")
    suspend fun find(pkg: String): SubscriptionEntity?

    @Upsert
    suspend fun upsert(sub: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY priceCents ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Upsert
    suspend fun upsert(product: ProductEntity)

    @Query("SELECT * FROM products WHERE sku = :sku")
    suspend fun find(sku: String): ProductEntity?

    @Delete
    suspend fun delete(product: ProductEntity)
}

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY purchaseTimeMillis DESC")
    fun observeAll(): Flow<List<PurchaseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(purchase: PurchaseEntity)

    @Query("UPDATE purchases SET state = :state WHERE orderId = :orderId")
    suspend fun updateState(orderId: String, state: String)

    @Query("DELETE FROM purchases")
    suspend fun clear()
}

@Dao
interface PatchDao {
    @Query("SELECT * FROM patches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PatchEntity>>

    @Query("SELECT * FROM patches WHERE id = :id")
    suspend fun find(id: Long): PatchEntity?

    @Upsert
    suspend fun upsert(patch: PatchEntity): Long

    @Query("UPDATE patches SET applied = :applied WHERE id = :id")
    suspend fun setApplied(id: Long, applied: Boolean)

    @Delete
    suspend fun delete(patch: PatchEntity)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshots WHERE packageName = :pkg ORDER BY createdAt DESC")
    suspend fun forPackage(pkg: String): List<SnapshotEntity>

    @Insert
    suspend fun insert(snapshot: SnapshotEntity): Long

    @Delete
    suspend fun delete(snapshot: SnapshotEntity)
}

@Dao
interface LicenseConfigDao {
    @Query("SELECT * FROM license_configs")
    fun observeAll(): Flow<List<LicenseConfigEntity>>

    @Query("SELECT * FROM license_configs WHERE packageName = :pkg")
    suspend fun find(pkg: String): LicenseConfigEntity?

    @Upsert
    suspend fun upsert(config: LicenseConfigEntity)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<LogEntity>>

    @Insert
    suspend fun insert(log: LogEntity)

    @Query("DELETE FROM logs")
    suspend fun clear()
}
