package com.sandbox.patchsim.core.model

/**
 * Framework-independent domain model. These types contain no Android
 * dependencies so the simulation logic can be exercised by plain JVM unit tests.
 */

/** Subscription tiers the simulator can reproduce for a target app. */
enum class SubscriptionTier { FREE, PREMIUM, PRO }

/** Lifecycle status of a simulated subscription. */
enum class SubscriptionStatus { NONE, ACTIVE, EXPIRED, IN_GRACE, CANCELED }

/** State of a simulated purchase (mirrors a typical billing lifecycle). */
enum class PurchaseState { PENDING, PURCHASED, REFUNDED, CANCELED }

/** Kinds of local, reversible patches the manager understands. */
enum class PatchType {
    UNLOCK_PREMIUM_FLAG,   // flips the app's own premium entitlement flag (sandbox)
    FORCE_SUBSCRIPTION,    // pins a subscription tier/status
    FREE_IN_APP_PRICE,     // presents a test SKU at a fictitious price (e.g. R$ 0,00)
    MOCK_LICENSE_VERDICT,  // routes license checks to the local mock server
    CUSTOM_KEY_VALUE       // arbitrary sandbox key/value entitlement
}

/** Verdicts the local mock license server can return. */
enum class LicenseVerdict { LICENSED, NOT_LICENSED, RETRY, ERROR_SERVER }

/** Log severity. */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** A registered target: the demo app or an app the developer declares as owned/authorized. */
data class TargetApp(
    val packageName: String,
    val label: String,
    val versionName: String = "",
    val versionCode: Long = 0,
    val authorizedByUser: Boolean = false,
    val isDemo: Boolean = false,
    val notes: String = ""
)

/** A product that can be purchased inside the local sandbox. */
data class SimProduct(
    val sku: String,
    val title: String,
    val priceCents: Long,
    val currency: String = "BRL",
    val subscriptionTier: SubscriptionTier? = null
) {
    val isFree: Boolean get() = priceCents == 0L
}

data class SimPurchase(
    val sku: String,
    val packageName: String,
    val priceCents: Long,
    val currency: String,
    val state: PurchaseState,
    val orderId: String,
    val purchaseTimeMillis: Long
)

data class SubscriptionState(
    val packageName: String,
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    val expiryMillis: Long?   // null == perpetual / no expiry
)

data class PatchDefinition(
    val id: Long = 0,
    val name: String,
    val type: PatchType,
    val targetPackage: String,
    val description: String = "",
    val payload: Map<String, String> = emptyMap(),
    val applied: Boolean = false
)

/** A snapshot taken before applying a patch, used to revert. */
data class EntitlementSnapshot(
    val packageName: String,
    val premiumUnlocked: Boolean,
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    val expiryMillis: Long?,
    val extras: Map<String, String> = emptyMap()
)

/** Result of a license validation against the local mock server. */
data class LicenseResponse(
    val packageName: String,
    val verdict: LicenseVerdict,
    val allowAccess: Boolean,
    val reason: String,
    val nonce: String
)
