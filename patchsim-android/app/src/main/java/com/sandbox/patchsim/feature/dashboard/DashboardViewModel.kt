package com.sandbox.patchsim.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.patchsim.core.model.SubscriptionState
import com.sandbox.patchsim.data.repo.SandboxRepository
import com.sandbox.patchsim.data.server.MockLicenseServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val targetCount: Int = 0,
    val subscriptions: List<SubscriptionState> = emptyList(),
    val purchaseCount: Int = 0,
    val patchCount: Int = 0,
    val appliedPatchCount: Int = 0,
    val serverRunning: Boolean = false,
    val serverUrl: String = "-"
)

class DashboardViewModel(
    private val repo: SandboxRepository,
    private val licenseServer: MockLicenseServer
) : ViewModel() {

    private val serverState = MutableStateFlow(serverSnapshot())

    val uiState: StateFlow<DashboardUiState> = combine(
        repo.targets, repo.subscriptions, repo.purchases, repo.patches, serverState
    ) { targets, subs, purchases, patches, server ->
        DashboardUiState(
            targetCount = targets.size,
            subscriptions = subs,
            purchaseCount = purchases.size,
            patchCount = patches.size,
            appliedPatchCount = patches.count { it.applied },
            serverRunning = server.first,
            serverUrl = server.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun toggleServer() {
        viewModelScope.launch(Dispatchers.IO) {
            if (licenseServer.isRunning) licenseServer.stop() else licenseServer.start()
            serverState.value = serverSnapshot()
        }
    }

    private fun serverSnapshot(): Pair<Boolean, String> =
        licenseServer.isRunning to if (licenseServer.isRunning) licenseServer.baseUrl else "stopped"
}
