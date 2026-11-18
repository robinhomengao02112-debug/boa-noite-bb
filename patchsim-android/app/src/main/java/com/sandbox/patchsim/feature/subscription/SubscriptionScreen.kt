package com.sandbox.patchsim.feature.subscription

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandbox.patchsim.core.model.SubscriptionState
import com.sandbox.patchsim.core.model.SubscriptionTier
import com.sandbox.patchsim.ui.common.SectionCard
import com.sandbox.patchsim.ui.common.StatRow
import com.sandbox.patchsim.ui.common.rememberVm

@Composable
fun SubscriptionScreen() {
    val vm: SubscriptionViewModel = rememberVm()
    val state by vm.uiState.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize()) {
        items(state.targets) { target ->
            val sub = state.subscriptions.firstOrNull { it.packageName == target.packageName }
            SectionCard(target.label, target.packageName) {
                if (sub != null) {
                    StatRow("Tier", sub.tier.name)
                    StatRow("Status", sub.status.name)
                    StatRow("Com direito (entitled)", if (vm.entitlement(sub)) "SIM" else "não")
                } else {
                    Text("Sem assinatura configurada.", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                Text("Reproduzir estado:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowButtons(
                    pkg = target.packageName,
                    onFree = { vm.setFree(target.packageName) },
                    onActive = { tier -> vm.setActive(target.packageName, tier) },
                    onExpired = { tier -> vm.setExpired(target.packageName, tier) }
                )
            }
        }
    }
}

@Composable
private fun FlowButtons(
    pkg: String,
    onFree: () -> Unit,
    onActive: (SubscriptionTier) -> Unit,
    onExpired: (SubscriptionTier) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = onFree, label = { Text("Free") })
            Spacer(Modifier.width(6.dp))
            AssistChip(onClick = { onActive(SubscriptionTier.PREMIUM) }, label = { Text("Premium ativo") })
            Spacer(Modifier.width(6.dp))
            AssistChip(onClick = { onActive(SubscriptionTier.PRO) }, label = { Text("Pro ativo") })
        }
        Spacer(Modifier.height(6.dp))
        Row {
            AssistChip(onClick = { onExpired(SubscriptionTier.PREMIUM) }, label = { Text("Premium expirado") })
            Spacer(Modifier.width(6.dp))
            AssistChip(onClick = { onExpired(SubscriptionTier.PRO) }, label = { Text("Pro expirado") })
        }
    }
}
