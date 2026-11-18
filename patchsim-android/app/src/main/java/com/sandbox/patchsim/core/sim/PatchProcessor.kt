package com.sandbox.patchsim.core.sim

import com.sandbox.patchsim.core.model.EntitlementSnapshot
import com.sandbox.patchsim.core.model.PatchDefinition
import com.sandbox.patchsim.core.model.PatchType
import com.sandbox.patchsim.core.model.SubscriptionStatus
import com.sandbox.patchsim.core.model.SubscriptionTier

/**
 * Applies and reverts patches against an in-memory [EntitlementSnapshot] that
 * represents the sandbox entitlement state of a target app the developer owns.
 *
 * IMPORTANT (by design): a patch never rewrites a third-party binary. It only
 * transforms a local entitlement model, and every apply returns the prior
 * snapshot so the change is fully reversible (backup / restore).
 */
class PatchProcessor {

    data class ApplyResult(
        val before: EntitlementSnapshot,
        val after: EntitlementSnapshot
    )

    /** Apply [patch] to [current], returning before/after snapshots. */
    fun apply(patch: PatchDefinition, current: EntitlementSnapshot): ApplyResult {
        val after = when (patch.type) {
            PatchType.UNLOCK_PREMIUM_FLAG ->
                current.copy(premiumUnlocked = true)

            PatchType.FORCE_SUBSCRIPTION -> {
                val tier = patch.payload["tier"]?.let { runCatching { SubscriptionTier.valueOf(it) }.getOrNull() }
                    ?: SubscriptionTier.PRO
                val status = patch.payload["status"]?.let { runCatching { SubscriptionStatus.valueOf(it) }.getOrNull() }
                    ?: SubscriptionStatus.ACTIVE
                val expiry = patch.payload["expiryMillis"]?.toLongOrNull()
                current.copy(
                    premiumUnlocked = tier != SubscriptionTier.FREE,
                    tier = tier,
                    status = status,
                    expiryMillis = expiry
                )
            }

            PatchType.FREE_IN_APP_PRICE -> {
                val sku = patch.payload["sku"] ?: "default"
                current.copy(extras = current.extras + ("price_override:$sku" to "0"))
            }

            PatchType.MOCK_LICENSE_VERDICT -> {
                val verdict = patch.payload["verdict"] ?: "LICENSED"
                current.copy(extras = current.extras + ("license_route" to verdict))
            }

            PatchType.CUSTOM_KEY_VALUE -> {
                val key = patch.payload["key"] ?: return ApplyResult(current, current)
                val value = patch.payload["value"] ?: ""
                current.copy(extras = current.extras + (key to value))
            }
        }
        return ApplyResult(before = current, after = after)
    }

    /** Revert to a previously captured [snapshot]. Returns the snapshot unchanged. */
    fun revert(snapshot: EntitlementSnapshot): EntitlementSnapshot = snapshot
}
