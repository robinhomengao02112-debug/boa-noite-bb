package com.sandbox.patchsim.data.repo

import com.sandbox.patchsim.core.model.*
import com.sandbox.patchsim.core.sim.BillingSimulator
import com.sandbox.patchsim.core.sim.LicensePolicy
import com.sandbox.patchsim.core.sim.PatchProcessor
import com.sandbox.patchsim.core.sim.SubscriptionSimulator
import com.sandbox.patchsim.data.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single repository that fronts all sandbox state. It persists via Room and
 * delegates the actual rules to the pure simulators in core/sim. Everything here
 * is local; no network or real store is ever contacted.
 */
class SandboxRepository(
    private val targetDao: TargetAppDao,
    private val subscriptionDao: SubscriptionDao,
    private val productDao: ProductDao,
    private val purchaseDao: PurchaseDao,
    private val patchDao: PatchDao,
    private val snapshotDao: SnapshotDao,
    private val licenseDao: LicenseConfigDao,
    private val subscriptionSim: SubscriptionSimulator,
    private val billingSim: BillingSimulator,
    private val patchProcessor: PatchProcessor,
    private val licensePolicy: LicensePolicy,
    private val log: LogRepository
) {
    // ---- Targets ----
    val targets: Flow<List<TargetApp>> = targetDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun registerTarget(app: TargetApp) {
        targetDao.upsert(app.toEntity())
        log.i("Target", "Registered ${app.packageName} (authorized=${app.authorizedByUser}, demo=${app.isDemo})")
    }

    suspend fun removeTarget(app: TargetApp) = targetDao.delete(app.toEntity())

    suspend fun targetCount(): Int = targetDao.count()

    // ---- Subscriptions ----
    val subscriptions: Flow<List<SubscriptionState>> =
        subscriptionDao.observeAll().map { list -> list.map { subscriptionSim.evaluate(it.toModel()) } }

    suspend fun setSubscription(state: SubscriptionState) {
        subscriptionDao.upsert(state.toEntity())
        log.i("Subscription", "${state.packageName}: ${state.tier}/${state.status} expiry=${state.expiryMillis}")
    }

    fun newFree(pkg: String) = subscriptionSim.free(pkg)
    fun newActive(pkg: String, tier: SubscriptionTier, durationMillis: Long) =
        subscriptionSim.active(pkg, tier, durationMillis)
    fun newExpired(pkg: String, tier: SubscriptionTier, agoMillis: Long) =
        subscriptionSim.expired(pkg, tier, agoMillis)
    fun hasEntitlement(state: SubscriptionState) = subscriptionSim.hasEntitlement(state)

    // ---- Products & purchases ----
    val products: Flow<List<SimProduct>> = productDao.observeAll().map { list -> list.map { it.toModel() } }
    val purchases: Flow<List<SimPurchase>> = purchaseDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun upsertProduct(product: SimProduct, packageName: String) {
        billingSim.registerProduct(product)
        productDao.upsert(
            ProductEntity(product.sku, packageName, product.title, product.priceCents, product.currency, product.subscriptionTier?.name)
        )
    }

    suspend fun simulatePurchase(sku: String, packageName: String, overridePriceCents: Long?): SimPurchase {
        // Ensure the simulator knows the product (rehydrate from DB if needed).
        if (billingSim.product(sku) == null) {
            productDao.find(sku)?.let { billingSim.registerProduct(it.toModel()) }
        }
        val purchase = billingSim.purchase(sku, packageName, overridePriceCents)
        purchaseDao.insert(purchase.toEntity())
        val priceLabel = formatPrice(purchase.priceCents, purchase.currency)
        log.i("Billing", "Simulated purchase $sku for $packageName at $priceLabel (order ${purchase.orderId})")
        return purchase
    }

    suspend fun refund(orderId: String) {
        billingSim.refund(orderId)?.let {
            purchaseDao.updateState(orderId, it.state.name)
            log.w("Billing", "Refunded order $orderId")
        }
    }

    suspend fun clearPurchases() { billingSim.clear(); purchaseDao.clear() }

    // ---- Patches & snapshots (backup/restore) ----
    val patches: Flow<List<PatchDefinition>> = patchDao.observeAll().map { list -> list.map { it.toModel() } }
    val snapshots: Flow<List<SnapshotEntity>> = snapshotDao.observeAll()

    suspend fun savePatch(patch: PatchDefinition): Long {
        val id = patchDao.upsert(patch.toEntity())
        log.i("Patch", "Saved patch '${patch.name}' type=${patch.type} target=${patch.targetPackage}")
        return id
    }

    suspend fun deletePatch(patch: PatchDefinition) = patchDao.delete(patch.toEntity())

    /** Apply a patch: snapshot current entitlement first (backup), then transform & persist. */
    suspend fun applyPatch(patch: PatchDefinition): PatchProcessor.ApplyResult {
        val current = currentSnapshot(patch.targetPackage)
        // Backup before mutating.
        snapshotDao.insert(current.toSnapshotEntity("auto-backup before '${patch.name}'"))
        val result = patchProcessor.apply(patch, current)
        persistSnapshotAsState(result.after)
        patchDao.setApplied(patch.id, true)
        log.i("Patch", "Applied '${patch.name}'. premium ${current.premiumUnlocked} -> ${result.after.premiumUnlocked}")
        return result
    }

    suspend fun revertPatch(patch: PatchDefinition) {
        val backups = snapshotDao.forPackage(patch.targetPackage)
        val restore = backups.firstOrNull()?.toModel() ?: run {
            log.w("Patch", "No backup found for ${patch.targetPackage}; reverting to FREE baseline")
            EntitlementSnapshot(patch.targetPackage, false, SubscriptionTier.FREE, SubscriptionStatus.NONE, null)
        }
        persistSnapshotAsState(restore)
        patchDao.setApplied(patch.id, false)
        log.i("Patch", "Reverted '${patch.name}' -> premium=${restore.premiumUnlocked}, tier=${restore.tier}")
    }

    suspend fun saveSnapshot(packageName: String, label: String) {
        snapshotDao.insert(currentSnapshot(packageName).toSnapshotEntity(label))
        log.i("Backup", "Saved snapshot '$label' for $packageName")
    }

    suspend fun restoreSnapshot(snapshot: SnapshotEntity) {
        persistSnapshotAsState(snapshot.toModel())
        log.i("Backup", "Restored snapshot '${snapshot.label}' for ${snapshot.packageName}")
    }

    suspend fun deleteSnapshot(snapshot: SnapshotEntity) = snapshotDao.delete(snapshot)

    // ---- License mock config ----
    val licenseConfigs: Flow<List<LicenseConfigEntity>> = licenseDao.observeAll()

    suspend fun setLicenseVerdict(packageName: String, verdict: LicenseVerdict) {
        licensePolicy.configure(packageName, verdict)
        licenseDao.upsert(LicenseConfigEntity(packageName, verdict.name))
        log.i("License", "Configured $packageName -> $verdict")
    }

    suspend fun hydrateLicensePolicy() {
        licenseDao.find("") // no-op warmup to keep DAO referenced
    }

    // ---- helpers ----
    private suspend fun currentSnapshot(packageName: String): EntitlementSnapshot {
        val sub = subscriptionDao.find(packageName)?.toModel()
        return EntitlementSnapshot(
            packageName = packageName,
            premiumUnlocked = sub != null && hasEntitlement(sub),
            tier = sub?.tier ?: SubscriptionTier.FREE,
            status = sub?.status ?: SubscriptionStatus.NONE,
            expiryMillis = sub?.expiryMillis
        )
    }

    private suspend fun persistSnapshotAsState(snapshot: EntitlementSnapshot) {
        val status = if (snapshot.premiumUnlocked && snapshot.status == SubscriptionStatus.NONE)
            SubscriptionStatus.ACTIVE else snapshot.status
        subscriptionDao.upsert(
            SubscriptionEntity(snapshot.packageName, snapshot.tier.name, status.name, snapshot.expiryMillis)
        )
    }

    companion object {
        fun formatPrice(cents: Long, currency: String): String {
            val value = cents / 100.0
            return if (currency == "BRL") "R$ %.2f".format(value).replace('.', ',')
            else "%s %.2f".format(currency, value)
        }
    }
}

private fun EntitlementSnapshot.toSnapshotEntity(label: String) = SnapshotEntity(
    packageName = packageName,
    label = label,
    premiumUnlocked = premiumUnlocked,
    tier = tier.name,
    status = status.name,
    expiryMillis = expiryMillis,
    extrasJson = JsonMap.encode(extras)
)
