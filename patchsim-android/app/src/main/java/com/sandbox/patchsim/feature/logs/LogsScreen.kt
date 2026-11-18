package com.sandbox.patchsim.feature.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandbox.patchsim.ui.common.rememberVm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen() {
    val vm: LogsViewModel = rememberVm()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Logs de execução (${logs.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = vm::clear) { Text("Limpar") }
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(logs) { log ->
                val color = when (log.level) {
                    "ERROR" -> Color(0xFFB3261E)
                    "WARN" -> Color(0xFF8C5000)
                    "DEBUG" -> Color(0xFF49454F)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "${fmt.format(Date(log.timestamp))}  ${log.level}/${log.tag}",
                        style = MaterialTheme.typography.labelSmall, color = color, fontFamily = FontFamily.Monospace
                    )
                    Text(log.message, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
}
