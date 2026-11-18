package com.sandbox.patchsim.data.repo

import com.sandbox.patchsim.core.model.*
import com.sandbox.patchsim.data.db.*

fun TargetAppEntity.toModel() = TargetApp(packageName, label, versionName, versionCode, authorizedByUser, isDemo, notes)
fun TargetApp.toEntity() = TargetAppEntity(packageName, label, versionName, versionCode, authorizedByUser, isDemo, notes)

fun SubscriptionEntity.toModel() = SubscriptionState(
    packageName,
    runCatching { SubscriptionTier.valueOf(tier) }.getOrDefault(SubscriptionTier.FREE),
    runCatching { SubscriptionStatus.valueOf(status) }.getOrDefault(SubscriptionStatus.NONE),
    expiryMillis
)
fun SubscriptionState.toEntity() = SubscriptionEntity(packageName, tier.name, status.name, expiryMillis)

fun ProductEntity.toModel() = SimProduct(
    sku, title, priceCents, currency,
    tier?.let { runCatching { SubscriptionTier.valueOf(it) }.getOrNull() }
)

fun PurchaseEntity.toModel() = SimPurchase(
    sku, packageName, priceCents, currency,
    runCatching { PurchaseState.valueOf(state) }.getOrDefault(PurchaseState.PENDING),
    orderId, purchaseTimeMillis
)
fun SimPurchase.toEntity() = PurchaseEntity(orderId, sku, packageName, priceCents, currency, state.name, purchaseTimeMillis)

fun PatchEntity.toModel() = PatchDefinition(
    id, name,
    runCatching { PatchType.valueOf(type) }.getOrDefault(PatchType.CUSTOM_KEY_VALUE),
    targetPackage, description, JsonMap.decode(payloadJson), applied
)
fun PatchDefinition.toEntity() = PatchEntity(id, name, type.name, targetPackage, description, JsonMap.encode(payload), applied)

fun SnapshotEntity.toModel() = EntitlementSnapshot(
    packageName, premiumUnlocked,
    runCatching { SubscriptionTier.valueOf(tier) }.getOrDefault(SubscriptionTier.FREE),
    runCatching { SubscriptionStatus.valueOf(status) }.getOrDefault(SubscriptionStatus.NONE),
    expiryMillis, JsonMap.decode(extrasJson)
)
