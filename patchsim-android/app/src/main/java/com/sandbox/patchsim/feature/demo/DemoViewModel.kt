package com.sandbox.patchsim.feature.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.patchsim.core.model.SubscriptionState
import com.sandbox.patchsim.core.model.SubscriptionStatus
import com.sandbox.patchsim.core.model.SubscriptionTier
import com.sandbox.patchsim.data.repo.SandboxRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DemoUiState(
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val status: SubscriptionStatus = SubscriptionStatus.NONE,
    val entitled: Boolean = false
)

class DemoViewModel(private val repo: SandboxRepository) : ViewModel() {

    val uiState: StateFlow<DemoUiState> =
        repo.subscriptions.map { subs ->
            val sub: SubscriptionState? = subs.firstOrNull { it.packageName == DemoApp.PACKAGE }
            if (sub == null) DemoUiState()
            else DemoUiState(sub.tier, sub.status, repo.hasEntitlement(sub))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DemoUiState())

    fun features() = DemoApp.features

    fun isUnlocked(feature: DemoApp.Feature, state: DemoUiState): Boolean =
        DemoApp.isUnlocked(feature, state.tier, state.entitled)
}
