package com.sandbox.patchsim

import com.sandbox.patchsim.core.model.SubscriptionStatus
import com.sandbox.patchsim.core.model.SubscriptionTier
import com.sandbox.patchsim.core.sim.SubscriptionSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionSimulatorTest {

    private val day = 24L * 60 * 60 * 1000

    @Test fun free_has_no_entitlement() {
        val sim = SubscriptionSimulator(clock = { 1_000_000 })
        val state = sim.free("pkg")
        assertEquals(SubscriptionTier.FREE, state.tier)
        assertFalse(sim.hasEntitlement(state))
    }

    @Test fun active_premium_is_entitled() {
        val now = 1_000_000L
        val sim = SubscriptionSimulator(clock = { now })
        val state = sim.active("pkg", SubscriptionTier.PREMIUM, 30 * day)
        assertEquals(SubscriptionStatus.ACTIVE, sim.evaluate(state).status)
        assertTrue(sim.hasEntitlement(state))
    }

    @Test fun expired_beyond_grace_loses_entitlement() {
        var now = 1_000_000L
        val sim = SubscriptionSimulator(clock = { now })
        sim.graceWindowMillis = 3 * day
        val state = sim.active("pkg", SubscriptionTier.PRO, 1 * day)
        now += 10 * day // well past expiry + grace
        val evaluated = sim.evaluate(state)
        assertEquals(SubscriptionStatus.EXPIRED, evaluated.status)
        assertFalse(sim.hasEntitlement(state))
    }

    @Test fun within_grace_keeps_entitlement() {
        var now = 1_000_000L
        val sim = SubscriptionSimulator(clock = { now })
        sim.graceWindowMillis = 3 * day
        val state = sim.active("pkg", SubscriptionTier.PREMIUM, 1 * day)
        now += 2 * day // expired 1 day ago, still inside 3-day grace
        assertEquals(SubscriptionStatus.IN_GRACE, sim.evaluate(state).status)
        assertTrue(sim.hasEntitlement(state))
    }

    @Test fun explicit_expired_builder() {
        val sim = SubscriptionSimulator(clock = { 1_000_000 })
        val state = sim.expired("pkg", SubscriptionTier.PREMIUM, 5 * day)
        assertEquals(SubscriptionStatus.EXPIRED, state.status)
    }
}
