package com.sandbox.patchsim

import com.sandbox.patchsim.core.model.*
import com.sandbox.patchsim.di.ServiceLocator
import com.sandbox.patchsim.feature.demo.DemoApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Seeds the bundled demo app + sandbox catalog on first launch so every
 *  simulator has verifiable sample data immediately. */
object SandboxSeeder {

    fun seedIfEmpty(services: ServiceLocator) {
        val repo = services.sandboxRepository
        services.appScope.launch(Dispatchers.IO) {
            if (repo.targetCount() > 0) {
                // Re-hydrate billing catalog + license policy into the in-memory engines.
                return@launch
            }
            // 1) Register the in-app demo "premium" app as a target.
            repo.registerTarget(
                TargetApp(
                    packageName = DemoApp.PACKAGE,
                    label = "Demo Premium App (built-in)",
                    versionName = "1.0.0",
                    versionCode = 1,
                    authorizedByUser = true,
                    isDemo = true,
                    notes = "Bundled app used to validate the simulators end-to-end."
                )
            )
            // 2) Sandbox product catalog (note the R$ 0,00 test SKU).
            repo.upsertProduct(SimProduct("demo.premium.monthly", "Premium Monthly", 1990, "BRL", SubscriptionTier.PREMIUM), DemoApp.PACKAGE)
            repo.upsertProduct(SimProduct("demo.pro.yearly", "Pro Yearly", 19900, "BRL", SubscriptionTier.PRO), DemoApp.PACKAGE)
            repo.upsertProduct(SimProduct("demo.remove.ads", "Remove Ads (one-off)", 990, "BRL", null), DemoApp.PACKAGE)
            repo.upsertProduct(SimProduct("android.test.free", "Test SKU (free)", 0, "BRL", null), DemoApp.PACKAGE)

            // 3) Start as a Free user.
            repo.setSubscription(repo.newFree(DemoApp.PACKAGE))

            // 4) Default the mock license verdict to LICENSED.
            repo.setLicenseVerdict(DemoApp.PACKAGE, LicenseVerdict.LICENSED)

            // 5) Example reversible patches.
            repo.savePatch(
                PatchDefinition(
                    name = "Unlock Premium (demo)",
                    type = PatchType.UNLOCK_PREMIUM_FLAG,
                    targetPackage = DemoApp.PACKAGE,
                    description = "Flips the demo app's own premium entitlement flag in the sandbox."
                )
            )
            repo.savePatch(
                PatchDefinition(
                    name = "Force PRO subscription (demo)",
                    type = PatchType.FORCE_SUBSCRIPTION,
                    targetPackage = DemoApp.PACKAGE,
                    description = "Pins an active PRO subscription state.",
                    payload = mapOf("tier" to "PRO", "status" to "ACTIVE")
                )
            )
        }
    }
}
