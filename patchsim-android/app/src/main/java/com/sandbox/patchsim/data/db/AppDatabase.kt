package com.sandbox.patchsim.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TargetAppEntity::class,
        SubscriptionEntity::class,
        ProductEntity::class,
        PurchaseEntity::class,
        PatchEntity::class,
        SnapshotEntity::class,
        LicenseConfigEntity::class,
        LogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetAppDao(): TargetAppDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun productDao(): ProductDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun patchDao(): PatchDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun licenseConfigDao(): LicenseConfigDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "patchsim.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
