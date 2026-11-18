package com.sandbox.patchsim

import com.sandbox.patchsim.core.model.PurchaseState
import com.sandbox.patchsim.core.model.SimProduct
import com.sandbox.patchsim.core.sim.BillingSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingSimulatorTest {

    private fun sim(): BillingSimulator {
        var counter = 0
        return BillingSimulator(clock = { 42L }, orderIdFactory = { "ORDER-${counter++}" })
    }

    @Test fun purchase_uses_catalog_price() {
        val sim = sim()
        sim.registerProduct(SimProduct("sku.a", "A", 1990, "BRL"))
        val p = sim.purchase("sku.a", "pkg")
        assertEquals(1990, p.priceCents)
        assertEquals(PurchaseState.PURCHASED, p.state)
        assertTrue(sim.owns("pkg", "sku.a"))
    }

    @Test fun override_price_zero_is_allowed() {
        val sim = sim()
        sim.registerProduct(SimProduct("sku.paid", "Paid", 4990, "BRL"))
        val p = sim.purchase("sku.paid", "pkg", overridePriceCents = 0L)
        assertEquals(0L, p.priceCents)
        assertTrue(p.sku == "sku.paid")
    }

    @Test fun refund_transitions_state_and_revokes_ownership() {
        val sim = sim()
        sim.registerProduct(SimProduct("sku.a", "A", 1000, "BRL"))
        val p = sim.purchase("sku.a", "pkg")
        val refunded = sim.refund(p.orderId)
        assertEquals(PurchaseState.REFUNDED, refunded?.state)
        assertFalse(sim.owns("pkg", "sku.a"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknown_sku_throws() {
        sim().purchase("missing", "pkg")
    }

    @Test fun pending_then_acknowledge() {
        val sim = sim()
        sim.registerProduct(SimProduct("sku.a", "A", 1000, "BRL"))
        val p = sim.purchase("sku.a", "pkg", autoAcknowledge = false)
        assertEquals(PurchaseState.PENDING, p.state)
        val ack = sim.acknowledge(p.orderId)
        assertEquals(PurchaseState.PURCHASED, ack?.state)
    }
}
