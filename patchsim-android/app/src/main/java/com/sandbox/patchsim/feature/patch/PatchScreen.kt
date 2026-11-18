package com.sandbox.patchsim.feature.patch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandbox.patchsim.core.model.PatchType
import com.sandbox.patchsim.feature.demo.DemoApp
import com.sandbox.patchsim.ui.common.rememberVm

@Composable
fun PatchScreen() {
    val vm: PatchViewModel = rememberVm()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                text = { Text("Novo patch") },
                icon = {}
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.message?.let {
                AssistChip(onClick = {}, label = { Text(it) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.patches) { patch ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(patch.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (patch.applied) AssistChip(onClick = {}, label = { Text("aplicado") })
                            }
                            Text(patch.type.name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text(patch.targetPackage, style = MaterialTheme.typography.bodySmall)
                            if (patch.description.isNotBlank())
                                Text(patch.description, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Row {
                                if (patch.applied) {
                                    OutlinedButton(onClick = { vm.revert(patch) }) { Text("Reverter") }
                                } else {
                                    Button(onClick = { vm.apply(patch) }) { Text("Aplicar") }
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { vm.delete(patch) }) { Text("Excluir") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePatchDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, type, tier ->
                vm.create(name, type, DemoApp.PACKAGE, tier)
                showCreate = false
            }
        )
    }
}

@Composable
private fun CreatePatchDialog(
    onDismiss: () -> Unit,
    onCreate: (String, PatchType, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PatchType.UNLOCK_PREMIUM_FLAG) }
    var tier by remember { mutableStateOf("PRO") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name, type, if (type == PatchType.FORCE_SUBSCRIPTION) tier else null) }) {
                Text("Criar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text("Novo patch (demo)") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Text("Tipo:", style = MaterialTheme.typography.labelLarge)
                PatchType.values().forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Text(t.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    )
}
