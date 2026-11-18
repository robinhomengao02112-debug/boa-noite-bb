package com.sandbox.patchsim.feature.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandbox.patchsim.ui.common.SectionCard
import com.sandbox.patchsim.ui.common.rememberVm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen() {
    val vm: BackupViewModel = rememberVm()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        state.message?.let {
            AssistChip(onClick = {}, label = { Text(it) }, modifier = Modifier.padding(16.dp))
        }
        SectionCard("Criar backup do estado de entitlement") {
            state.targets.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(t.label, Modifier.weight(1f))
                    Button(onClick = { vm.backup(t.packageName, "Backup ${t.label}") }) { Text("Backup") }
                }
            }
        }
        Text("Snapshots (${state.snapshots.size})", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.snapshots) { snap ->
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(snap.label, style = MaterialTheme.typography.titleSmall)
                        Text(snap.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Text("premium=${snap.premiumUnlocked} · ${snap.tier}/${snap.status} · ${fmt.format(Date(snap.createdAt))}",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Button(onClick = { vm.restore(snap) }) { Text("Restaurar") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { vm.delete(snap) }) { Text("Excluir") }
                        }
                    }
                }
            }
        }
    }
}
