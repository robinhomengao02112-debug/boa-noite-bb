package com.sandbox.patchsim.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "target_apps")
data class TargetAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val versionName: String = "",
    val versionCode: Long = 0,
    val authorizedByUser: Boolean = false,
    val isDemo: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val packageName: String,
    val tier: String,         // SubscriptionTier
    val status: String,       // SubscriptionStatus
    val expiryMillis: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val sku: String,
    val packageName: String,
    val title: String,
    val priceCents: Long,
    val currency: String = "BRL",
    val tier: String? = null  // SubscriptionTier or null for one-off
)

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey val orderId: String,
    val sku: String,
    val packageName: String,
    val priceCents: Long,
    val currency: String,
    val state: String,        // PurchaseState
    val purchaseTimeMillis: Long
)

@Entity(tableName = "patches")
data class PatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,         // PatchType
    val targetPackage: String,
    val description: String = "",
    val payloadJson: String = "{}",
    val applied: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val premiumUnlocked: Boolean,
    val tier: String,
    val status: String,
    val expiryMillis: Long? = null,
    val extrasJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "license_configs")
data class LicenseConfigEntity(
    @PrimaryKey val packageName: String,
    val verdict: String,      // LicenseVerdict
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String,        // LogLevel
    val tag: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
