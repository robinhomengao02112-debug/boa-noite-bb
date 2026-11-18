package com.sandbox.patchsim.feature.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandbox.patchsim.ui.common.SectionCard
import com.sandbox.patchsim.ui.common.rememberVm

@Composable
fun DemoScreen() {
    val vm: DemoViewModel = rememberVm()
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionCard(
            "Demo Premium App",
            "Este app embutido valida os simuladores. Altere a assinatura na aba Assinaturas e veja os recursos abaixo reagirem em tempo real."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text("Tier: ${state.tier}") })
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, label = { Text("Status: ${state.status}") })
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.entitled) "Usuário com direito a recursos pagos" else "Usuário sem direito (free)",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        SectionCard("Recursos") {
            vm.features().forEach { feature ->
                val unlocked = vm.isUnlocked(feature, state)
                ListItem(
                    headlineContent = { Text(feature.name) },
                    supportingContent = { Text("${feature.description} (mín: ${feature.minTier})") },
                    leadingContent = {
                        if (unlocked) Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E7D32))
                        else Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.outline)
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
