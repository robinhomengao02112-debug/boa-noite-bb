package com.sandbox.patchsim.feature.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.patchsim.core.model.SubscriptionState
import com.sandbox.patchsim.core.model.SubscriptionTier
import com.sandbox.patchsim.core.model.TargetApp
import com.sandbox.patchsim.data.repo.SandboxRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SubscriptionUiState(
    val targets: List<TargetApp> = emptyList(),
    val subscriptions: List<SubscriptionState> = emptyList()
)

class SubscriptionViewModel(private val repo: SandboxRepository) : ViewModel() {

    val uiState: StateFlow<SubscriptionUiState> =
        combine(repo.targets, repo.subscriptions) { targets, subs ->
            SubscriptionUiState(targets, subs)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubscriptionUiState())

    private val day = 24L * 60 * 60 * 1000

    fun setFree(pkg: String) = update(repo.newFree(pkg))

    fun setActive(pkg: String, tier: SubscriptionTier, days: Long = 30) =
        update(repo.newActive(pkg, tier, days * day))

    fun setExpired(pkg: String, tier: SubscriptionTier, daysAgo: Long = 5) =
        update(repo.newExpired(pkg, tier, daysAgo * day))

    fun entitlement(state: SubscriptionState) = repo.hasEntitlement(state)

    private fun update(state: SubscriptionState) {
        viewModelScope.launch { repo.setSubscription(state) }
    }
}
