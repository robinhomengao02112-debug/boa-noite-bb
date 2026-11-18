package com.sandbox.patchsim

import com.sandbox.patchsim.core.model.EntitlementSnapshot
import com.sandbox.patchsim.core.model.PatchDefinition
import com.sandbox.patchsim.core.model.PatchType
import com.sandbox.patchsim.core.model.SubscriptionStatus
import com.sandbox.patchsim.core.model.SubscriptionTier
import com.sandbox.patchsim.core.sim.PatchProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchProcessorTest {

    private val base = EntitlementSnapshot(
        packageName = "pkg",
        premiumUnlocked = false,
        tier = SubscriptionTier.FREE,
        status = SubscriptionStatus.NONE,
        expiryMillis = null
    )

    @Test fun unlock_premium_flag_sets_flag_and_is_reversible() {
        val proc = PatchProcessor()
        val patch = PatchDefinition(name = "u", type = PatchType.UNLOCK_PREMIUM_FLAG, targetPackage = "pkg")
        val result = proc.apply(patch, base)
        assertFalse(result.before.premiumUnlocked)
        assertTrue(result.after.premiumUnlocked)
        // revert returns the captured 'before'
        val reverted = proc.revert(result.before)
        assertFalse(reverted.premiumUnlocked)
    }

    @Test fun force_subscription_sets_tier_and_status() {
        val proc = PatchProcessor()
        val patch = PatchDefinition(
            name = "pro", type = PatchType.FORCE_SUBSCRIPTION, targetPackage = "pkg",
            payload = mapOf("tier" to "PRO", "status" to "ACTIVE")
        )
        val after = proc.apply(patch, base).after
        assertEquals(SubscriptionTier.PRO, after.tier)
        assertEquals(SubscriptionStatus.ACTIVE, after.status)
        assertTrue(after.premiumUnlocked)
    }

    @Test fun free_price_override_recorded_in_extras() {
        val proc = PatchProcessor()
        val patch = PatchDefinition(
            name = "free", type = PatchType.FREE_IN_APP_PRICE, targetPackage = "pkg",
            payload = mapOf("sku" to "demo.pro.yearly")
        )
        val after = proc.apply(patch, base).after
        assertEquals("0", after.extras["price_override:demo.pro.yearly"])
    }

    @Test fun custom_key_value_applied() {
        val proc = PatchProcessor()
        val patch = PatchDefinition(
            name = "kv", type = PatchType.CUSTOM_KEY_VALUE, targetPackage = "pkg",
            payload = mapOf("key" to "feature.x", "value" to "on")
        )
        assertEquals("on", proc.apply(patch, base).after.extras["feature.x"])
    }
}
