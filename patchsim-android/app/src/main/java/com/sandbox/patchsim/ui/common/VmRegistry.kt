package com.sandbox.patchsim.ui.common

import androidx.lifecycle.ViewModel
import com.sandbox.patchsim.di.ServiceLocator
import com.sandbox.patchsim.feature.analyzer.AnalyzerViewModel
import com.sandbox.patchsim.feature.backup.BackupViewModel
import com.sandbox.patchsim.feature.dashboard.DashboardViewModel
import com.sandbox.patchsim.feature.demo.DemoViewModel
import com.sandbox.patchsim.feature.license.LicenseViewModel
import com.sandbox.patchsim.feature.logs.LogsViewModel
import com.sandbox.patchsim.feature.patch.PatchViewModel
import com.sandbox.patchsim.feature.purchase.PurchaseViewModel
import com.sandbox.patchsim.feature.subscription.SubscriptionViewModel

/** Central map of every feature ViewModel constructor. */
object VmRegistry {
    fun factory(services: ServiceLocator): VmFactory {
        val creators: Map<Class<*>, (ServiceLocator) -> ViewModel> = mapOf(
            DashboardViewModel::class.java to { s -> DashboardViewModel(s.sandboxRepository, s.licenseServer) },
            AnalyzerViewModel::class.java to { s -> AnalyzerViewModel(s.apkAnalyzer, s.sandboxRepository, s.logRepository) },
            SubscriptionViewModel::class.java to { s -> SubscriptionViewModel(s.sandboxRepository) },
            PurchaseViewModel::class.java to { s -> PurchaseViewModel(s.sandboxRepository) },
            PatchViewModel::class.java to { s -> PatchViewModel(s.sandboxRepository) },
            LicenseViewModel::class.java to { s -> LicenseViewModel(s.sandboxRepository, s.licenseServer) },
            BackupViewModel::class.java to { s -> BackupViewModel(s.sandboxRepository) },
            LogsViewModel::class.java to { s -> LogsViewModel(s.logRepository) },
            DemoViewModel::class.java to { s -> DemoViewModel(s.sandboxRepository) }
        )
        return VmFactory(services, creators)
    }
}
