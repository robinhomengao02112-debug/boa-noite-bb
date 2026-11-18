package com.sandbox.patchsim.core.sim

import com.sandbox.patchsim.core.model.PurchaseState
import com.sandbox.patchsim.core.model.SimProduct
import com.sandbox.patchsim.core.model.SimPurchase
import java.util.UUID

/**
 * Local, in-memory mock of a billing/purchase flow. It never contacts any real
 * store or payment system: it only produces fictitious purchase records for a
 * developer's own app, including test SKUs priced at R$ 0,00.
 *
 * This is the sandbox equivalent of Google Play's static test SKUs
 * (android.test.purchased, etc.), implemented fully offline.
 */
class BillingSimulator(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val orderIdFactory: () -> String = { "SIM-" + UUID.randomUUID().toString().take(12).uppercase() }
) {
    private val catalog = LinkedHashMap<String, SimProduct>()
    private val purchases = mutableListOf<SimPurchase>()

    fun registerProduct(product: SimProduct) { catalog[product.sku] = product }

    fun products(): List<SimProduct> = catalog.values.toList()

    fun product(sku: String): SimProduct? = catalog[sku]

    /**
     * Launch a simulated purchase. If [overridePriceCents] is provided it replaces
     * the catalog price for this transaction — this is how a tester presents a paid
     * SKU as R$ 0,00 inside the sandbox without touching any real price.
     */
    fun purchase(
        sku: String,
        packageName: String,
        overridePriceCents: Long? = null,
        autoAcknowledge: Boolean = true
    ): SimPurchase {
        val product = catalog[sku]
            ?: throw IllegalArgumentException("Unknown SKU in sandbox catalog: $sku")
        val price = overridePriceCents ?: product.priceCents
        require(price >= 0) { "Price cannot be negative" }
        val purchase = SimPurchase(
            sku = sku,
            packageName = packageName,
            priceCents = price,
            currency = product.currency,
            state = if (autoAcknowledge) PurchaseState.PURCHASED else PurchaseState.PENDING,
            orderId = orderIdFactory(),
            purchaseTimeMillis = clock()
        )
        purchases += purchase
        return purchase
    }

    /** Acknowledge a pending purchase (PENDING -> PURCHASED). */
    fun acknowledge(orderId: String): SimPurchase? =
        transition(orderId, from = PurchaseState.PENDING, to = PurchaseState.PURCHASED)

    /** Refund a completed purchase (PURCHASED -> REFUNDED). */
    fun refund(orderId: String): SimPurchase? =
        transition(orderId, from = PurchaseState.PURCHASED, to = PurchaseState.REFUNDED)

    /** Cancel a pending purchase (PENDING -> CANCELED). */
    fun cancel(orderId: String): SimPurchase? =
        transition(orderId, from = PurchaseState.PENDING, to = PurchaseState.CANCELED)

    fun purchases(packageName: String? = null): List<SimPurchase> =
        if (packageName == null) purchases.toList()
        else purchases.filter { it.packageName == packageName }

    /** True if the package currently owns an un-refunded, acknowledged purchase for [sku]. */
    fun owns(packageName: String, sku: String): Boolean =
        purchases.any {
            it.packageName == packageName && it.sku == sku &&
                it.state == PurchaseState.PURCHASED
        }

    fun clear() { purchases.clear() }

    private fun transition(orderId: String, from: PurchaseState, to: PurchaseState): SimPurchase? {
        val idx = purchases.indexOfFirst { it.orderId == orderId && it.state == from }
        if (idx < 0) return null
        val updated = purchases[idx].copy(state = to)
        purchases[idx] = updated
        return updated
    }
}
