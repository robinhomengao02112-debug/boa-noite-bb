package com.sandbox.patchsim.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandbox.patchsim.data.repo.SandboxRepository
import com.sandbox.patchsim.ui.common.SectionCard
import com.sandbox.patchsim.ui.common.StatRow
import com.sandbox.patchsim.ui.common.rememberVm

@Composable
fun DashboardScreen() {
    val vm: DashboardViewModel = rememberVm()
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        SectionCard("Resumo do laboratório", "Tudo roda localmente, em modo de simulação.") {
            StatRow("Apps de teste registrados", state.targetCount.toString())
            StatRow("Assinaturas simuladas", state.subscriptions.size.toString())
            StatRow("Compras simuladas", state.purchaseCount.toString())
            StatRow("Patches (aplicados)", "${state.patchCount} (${state.appliedPatchCount})")
        }

        SectionCard("Servidor de licença local (mock)", "Endpoint de teste em loopback (127.0.0.1).") {
            StatRow("Status", if (state.serverRunning) "Em execução" else "Parado")
            StatRow("URL", state.serverUrl)
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::toggleServer) {
                Text(if (state.serverRunning) "Parar servidor" else "Iniciar servidor")
            }
        }

        SectionCard("Estados de assinatura atuais") {
            if (state.subscriptions.isEmpty()) {
                Text("Nenhuma assinatura configurada ainda.", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.subscriptions.forEach { sub ->
                    StatRow(
                        sub.packageName,
                        "${sub.tier} / ${sub.status}"
                    )
                }
            }
        }
    }
}
