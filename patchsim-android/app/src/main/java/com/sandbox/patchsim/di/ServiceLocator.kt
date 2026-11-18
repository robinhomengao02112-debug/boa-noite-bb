package com.sandbox.patchsim.di

import android.content.Context
import com.sandbox.patchsim.core.sim.BillingSimulator
import com.sandbox.patchsim.core.sim.LicensePolicy
import com.sandbox.patchsim.core.sim.PatchProcessor
import com.sandbox.patchsim.core.sim.SubscriptionSimulator
import com.sandbox.patchsim.data.db.AppDatabase
import com.sandbox.patchsim.data.repo.ApkAnalyzer
import com.sandbox.patchsim.data.repo.LogRepository
import com.sandbox.patchsim.data.repo.SandboxRepository
import com.sandbox.patchsim.data.server.MockLicenseServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Lightweight manual dependency container (a Service Locator). Chosen over a
 * full DI framework to keep the module annotation-processor-free apart from
 * Room, while preserving clean, testable construction.
 */
class ServiceLocator(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val db = AppDatabase.get(context)

    // Pure engines (deterministic, unit-tested in isolation).
    val subscriptionSim = SubscriptionSimulator()
    val billingSim = BillingSimulator()
    val patchProcessor = PatchProcessor()
    val licensePolicy = LicensePolicy()

    val logRepository = LogRepository(db.logDao(), appScope)

    val apkAnalyzer = ApkAnalyzer(context)

    val sandboxRepository = SandboxRepository(
        targetDao = db.targetAppDao(),
        subscriptionDao = db.subscriptionDao(),
        productDao = db.productDao(),
        purchaseDao = db.purchaseDao(),
        patchDao = db.patchDao(),
        snapshotDao = db.snapshotDao(),
        licenseDao = db.licenseConfigDao(),
        subscriptionSim = subscriptionSim,
        billingSim = billingSim,
        patchProcessor = patchProcessor,
        licensePolicy = licensePolicy,
        log = logRepository
    )

    val licenseServer = MockLicenseServer(
        policy = licensePolicy,
        onLog = { logRepository.i("LicenseServer", it) }
    )
}
