package com.sandbox.patchsim.feature.analyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandbox.patchsim.data.repo.ApkReport
import com.sandbox.patchsim.ui.common.SectionCard
import com.sandbox.patchsim.ui.common.StatRow
import com.sandbox.patchsim.ui.common.rememberVm

@Composable
fun AnalyzerScreen() {
    val vm: AnalyzerViewModel = rememberVm()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (state.apps.isEmpty()) vm.refresh() }

    val selected = state.selected
    if (selected != null) {
        ApkDetail(selected, onBack = { vm.select(null) }, onRegister = { vm.registerAsTarget(selected) })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("Buscar app") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = vm::refresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = state.includeSystem, onCheckedChange = vm::setIncludeSystem)
            Spacer(Modifier.width(8.dp))
            Text("Incluir apps de sistema")
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.filtered) { app ->
                ListItem(
                    headlineContent = { Text(app.label) },
                    supportingContent = { Text(app.packageName, fontFamily = FontFamily.Monospace) },
                    trailingContent = { if (app.usesBilling) AssistChip(onClick = {}, label = { Text("billing") }) },
                    modifier = Modifier.clickable { vm.select(app) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ApkDetail(report: ApkReport, onBack: () -> Unit, onRegister: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(
            title = { Text(report.label) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            }
        )
        SectionCard("Identidade") {
            StatRow("Pacote", report.packageName)
            StatRow("Versão", "${report.versionName} (${report.versionCode})")
            StatRow("minSdk / targetSdk", "${report.minSdk} / ${report.targetSdk}")
            StatRow("Sistema", if (report.isSystemApp) "sim" else "não")
            StatRow("Debuggable", if (report.isDebuggable) "sim" else "não")
            StatRow("Instalador", report.installerPackage ?: "desconhecido")
        }
        SectionCard("Sinais de compras integradas", if (report.usesBilling) "Detectado" else "Nenhum sinal encontrado") {
            if (report.billingSignals.isEmpty()) Text("—")
            else report.billingSignals.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        SectionCard("Permissões (${report.permissions.size})") {
            report.permissions.take(40).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            if (report.permissions.size > 40) Text("… e mais ${report.permissions.size - 40}")
        }
        SectionCard("Componentes") {
            StatRow("Activities", report.activities.size.toString())
            StatRow("Services", report.services.size.toString())
            StatRow("Receivers", report.receivers.size.toString())
            StatRow("Providers", report.providers.size.toString())
            StatRow("ABIs nativas", report.nativeLibsAbis.joinToString().ifBlank { "—" })
        }
        Button(onClick = onRegister, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Registrar como app de teste autorizado")
        }
    }
}
