package com.sandbox.patchsim.feature.license

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandbox.patchsim.core.model.LicenseVerdict
import com.sandbox.patchsim.feature.demo.DemoApp
import com.sandbox.patchsim.ui.common.SectionCard
import com.sandbox.patchsim.ui.common.StatRow
import com.sandbox.patchsim.ui.common.rememberVm

@Composable
fun LicenseScreen() {
    val vm: LicenseViewModel = rememberVm()
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionCard("Servidor de licença local (mock)", "Loopback 127.0.0.1 — valida respostas LVL offline.") {
            StatRow("Status", if (state.serverRunning) "em execução" else "parado")
            StatRow("URL", state.serverUrl)
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = vm::toggleServer) { Text(if (state.serverRunning) "Parar" else "Iniciar") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { vm.testCheck() }) { Text("Testar verificação") }
            }
            state.lastResponse?.let {
                Spacer(Modifier.height(8.dp))
                Text("Resposta:", style = MaterialTheme.typography.labelLarge)
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.horizontalScroll(rememberScrollState()))
            }
        }
        SectionCard("Veredito por pacote") {
            val configs = state.configs.ifEmpty {
                listOf(com.sandbox.patchsim.data.db.LicenseConfigEntity(DemoApp.PACKAGE, "LICENSED"))
            }
            configs.forEach { cfg ->
                Text(cfg.packageName, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    LicenseVerdict.values().forEach { v ->
                        FilterChip(
                            selected = cfg.verdict == v.name,
                            onClick = { vm.setVerdict(cfg.packageName, v) },
                            label = { Text(v.name) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
