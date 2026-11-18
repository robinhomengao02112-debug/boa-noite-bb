package com.budscontrol.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budscontrol.app.protocol.hexToBytes

private val Orange = Color(0xFFFF6D00)
private val Bg = Color(0xFF0E1116)
private val CardBg = Color(0xFF181C23)
private val Muted = Color(0xFF9AA4B2)
private val Good = Color(0xFF4CAF50)
private val Warn = Color(0xFFFFB300)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Orange, onPrimary = Color.Black, secondary = Warn,
                    background = Bg, surface = CardBg, surfaceVariant = Color(0xFF232833),
                )
            ) { App() }
        }
    }
}

private fun Context.hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

@Composable
private fun App() {
    val ctx = LocalContext.current
    val state by BudsManager.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }

    val basePerms = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val btOk = Build.VERSION.SDK_INT < 31 || res[Manifest.permission.BLUETOOTH_CONNECT] == true ||
            ctx.hasPerm(Manifest.permission.BLUETOOTH_CONNECT)
        if (btOk) {
            if (Prefs.deviceAddress(ctx) == null) showPicker = true
            else BudsService.send(ctx, BudsService.ACTION_CONNECT)
        } else {
            Toast.makeText(ctx, "Sem a permissão de Bluetooth o app não consegue falar com os fones.", Toast.LENGTH_LONG).show()
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) BudsService.send(ctx, BudsService.ACTION_SMART_ON)
        else Toast.makeText(ctx, "O ANC Inteligente precisa do microfone para medir o ruído.", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(Unit) {
        val missing = basePerms.filterNot { ctx.hasPerm(it) }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
        else if (Prefs.deviceAddress(ctx) == null) showPicker = true
        else BudsService.send(ctx, BudsService.ACTION_CONNECT)
    }

    val toggleSmart: (Boolean) -> Unit = { on ->
        if (!on) BudsService.send(ctx, BudsService.ACTION_SMART_OFF)
        else if (ctx.hasPerm(Manifest.permission.RECORD_AUDIO)) BudsService.send(ctx, BudsService.ACTION_SMART_ON)
        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    if (showPicker) DevicePicker(onDismiss = { showPicker = false }) { addr ->
        Prefs.setDevice(ctx, addr)
        showPicker = false
        BudsManager.disconnect()
        BudsService.send(ctx, BudsService.ACTION_CONNECT)
    }

    val tabs = listOf("Início", "Som", "Controles", "Conexão", "Avançado")
    Scaffold(containerColor = Bg, topBar = {
        Column(Modifier.background(Bg)) {
            Header(state)
            ScrollableTabRow(selectedTabIndex = tab, containerColor = Bg, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeTab(state, toggleSmart)
                1 -> SoundTab(state)
                2 -> ControlsTab(state)
                3 -> ConnectionTab(state) { showPicker = true }
                4 -> AdvancedTab(state)
            }
        }
    }
}

@Composable
private fun Header(s: BudsState) {
    val color = when (s.link) {
        Link.CONNECTED -> Good
        Link.CONNECTING, Link.AUTHENTICATING -> Warn
        Link.ERROR -> Color(0xFFE53935)
        Link.DISCONNECTED -> Muted
    }
    Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp, 16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.deviceName ?: "Buds Control", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
            Text(s.statusText, color = Muted, fontSize = 13.sp, maxLines = 2)
        }
    }
}

// ============================================================== componentes

@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Choice(options: List<Pair<Int, String>>, selected: Int?, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            FilterChip(
                selected = selected == v, enabled = enabled, onClick = { onSelect(v) },
                label = { Text(label, fontSize = 13.sp) }, modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Toggle(title: String, desc: String?, value: Boolean?, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White)
            if (desc != null) Text(desc, color = Muted, fontSize = 12.sp)
        }
        Switch(checked = value == true, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun Picker(label: String, options: List<Pair<Int, String>>, selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }, enabled = enabled) {
                Text(options.firstOrNull { it.first == selected }?.second ?: "—", fontSize = 13.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (v, t) -> DropdownMenuItem(text = { Text(t) }, onClick = { open = false; onSelect(v) }) }
            }
        }
    }
}

@Composable
private fun BatteryBox(label: String, b: Battery?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF232833)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(b?.let { "${it.level}%" } ?: "—", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        if (b?.charging == true) Text("carregando", color = Good, fontSize = 11.sp)
        LinearProgressIndicator(
            progress = { (b?.level ?: 0) / 100f },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if ((b?.level ?: 100) <= 20) Color(0xFFE53935) else Good,
        )
    }
}

@Composable
private fun Scroll(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

// ============================================================== abas

@Composable
private fun HomeTab(s: BudsState, toggleSmart: (Boolean) -> Unit) {
    val on = s.link == Link.CONNECTED
    val ctx = LocalContext.current
    Scroll {
        Section("Bateria", s.inEar) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BatteryBox("Esquerdo", s.left, Modifier.weight(1f))
                BatteryBox("Direito", s.right, Modifier.weight(1f))
                BatteryBox("Estojo", s.case, Modifier.weight(1f))
            }
            if (s.left == null && s.systemBattery != null)
                Text("Nível informado pelo Android: ${s.systemBattery}%", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Section("Modo de som") {
            Choice(listOf(1 to "Cancelar ruído", 2 to "Transparência", 0 to "Desligado"), s.ambientMode, on) {
                BudsManager.setAmbientMode(it)
            }
        }

        if (s.ambientMode == 1 || s.ambientMode == null) Section("Intensidade do cancelamento", "Profundo = isolamento máximo do hardware") {
            Choice(
                listOf(BudsManager.ANC_LIGHT to "Leve", BudsManager.ANC_BALANCED to "Equilibrado", BudsManager.ANC_DEEP to "Profundo"),
                s.ancStrength, on && !s.smartAncOn
            ) { BudsManager.setAncStrength(it) }
            Toggle("ANC adaptativo do fone", "O próprio fone ajusta a intensidade (se o modelo suportar)", s.adaptiveAnc, on && !s.smartAncOn) {
                BudsManager.setAdaptiveAnc(it)
            }
        }

        if (s.ambientMode == 2) Section("Tipo de transparência") {
            Choice(listOf(0 to "Normal", 1 to "Voz", 2 to "Ambiente"), s.transparencyStrength, on) {
                BudsManager.setTransparencyStrength(it)
            }
        }

        Section("Turbo", "Força o máximo de cancelamento que os fones conseguem: ANC ligado, intensidade profunda e adaptativo desligado.") {
            Button(
                onClick = { BudsManager.turboAnc(); Toast.makeText(ctx, "Turbo ativado", Toast.LENGTH_SHORT).show() },
                enabled = on, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) { Text("ATIVAR TURBO ANC", fontWeight = FontWeight.Bold) }
        }

        Section("ANC Inteligente", "Mede o ruído com o microfone do celular e troca a intensidade automaticamente (silêncio → leve, rua/ônibus → profundo). Economiza bateria nos lugares calmos.") {
            Toggle("Ativar", null, s.smartAncOn, on || s.smartAncOn) { toggleSmart(it) }
            if (s.smartAncOn) {
                Text("Ruído medido: ${s.ambientDb?.let { "%.0f dB".format(it) } ?: "medindo…"}", color = Color.White)
                s.smartAncDecision?.let { Text("Decisão: $it", color = Muted, fontSize = 12.sp) }
            }
        }
    }
}

private val EQ_PRESETS = listOf(0 to "Padrão", 1 to "Voz", 5 to "Graves", 6 to "Agudos", BudsManager.EQ_CUSTOM to "Personalizado")
private val EQ_LABELS = listOf("62", "125", "250", "500", "1k", "2k", "4k", "8k", "12k", "16k")
private val EQ_CURVES = listOf(
    "Plano" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    "Mascarar ruído" to listOf(2, 2, 1, 0, 0, 1, 1, 0, 0, 0),
    "Graves forte" to listOf(6, 5, 3, 1, 0, 0, 0, 0, 0, 0),
    "Voz/podcast" to listOf(-3, -2, -1, 1, 3, 4, 3, 1, 0, -1),
    "Brilho" to listOf(0, 0, 0, 0, 0, 1, 3, 4, 4, 3),
    "Noturno (suave)" to listOf(1, 1, 0, 0, -1, -2, -3, -3, -4, -4),
)

@Composable
private fun SoundTab(s: BudsState) {
    val on = s.link == Link.CONNECTED
    var bands by remember(s.eqBands) { mutableStateOf(s.eqBands) }
    Scroll {
        Section("Equalizador do fone", "Aplicado direto no chip do fone: vale para qualquer app.") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EQ_PRESETS.take(3).forEach { (v, t) ->
                    FilterChip(selected = s.eqPreset == v, enabled = on, onClick = { BudsManager.setEqPreset(v) },
                        label = { Text(t, fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EQ_PRESETS.drop(3).forEach { (v, t) ->
                    FilterChip(selected = s.eqPreset == v, enabled = on, onClick = { BudsManager.setEqPreset(v) },
                        label = { Text(t, fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                }
            }
        }
        Section("EQ personalizado de 10 bandas", "−6 a +6 dB por banda") {
            Text("Curvas prontas", color = Muted, fontSize = 12.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EQ_CURVES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (name, c) ->
                            OutlinedButton(onClick = { bands = c }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                                Text(name, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            bands.forEachIndexed { i, v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(EQ_LABELS[i], color = Muted, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                    Slider(
                        value = v.toFloat(), valueRange = -6f..6f, steps = 11,
                        onValueChange = { nv -> bands = bands.toMutableList().also { it[i] = Math.round(nv) } },
                        modifier = Modifier.weight(1f)
                    )
                    Text("%+d".format(v), color = Color.White, fontSize = 12.sp, modifier = Modifier.width(32.dp))
                }
            }
            Button(onClick = { BudsManager.setCustomEq(bands) }, enabled = on, modifier = Modifier.fillMaxWidth()) {
                Text("Aplicar no fone")
            }
        }
        Section("Som adaptativo") {
            Toggle("Ajuste automático de som", "Recurso do firmware; pode não existir no seu modelo", s.adaptiveSound, on) {
                BudsManager.setAdaptiveSound(it)
            }
        }
    }
}

private val SINGLE_FUNCS = listOf(8 to "Nenhum", 1 to "Play/Pausa", 2 to "Anterior", 3 to "Próxima", 4 to "Volume +", 5 to "Volume −")
private val MULTI_FUNCS = SINGLE_FUNCS.drop(1)
private val LONG_MODES = listOf(0 to "Assistente de voz", 6 to "Alternar modos de som")
private val LONG_CYCLE = listOf(3 to "ANC ↔ Desligado", 5 to "Transparência ↔ Desligado", 6 to "ANC ↔ Transparência", 7 to "Todos")

@Composable
private fun ControlsTab(s: BudsState) {
    val on = s.link == Link.CONNECTED
    Scroll {
        Section("Recursos") {
            Toggle("Detecção de uso", "Pausa a música ao tirar o fone", s.earDetection, on) { BudsManager.setEarDetection(it) }
            Toggle("Atender chamadas automaticamente", "Ao colocar o fone no ouvido", s.autoAnswer, on) { BudsManager.setAutoAnswer(it) }
        }
        val names = listOf("Toque simples", "Toque duplo", "Toque triplo", "Toque longo")
        names.forEachIndexed { g, title ->
            Section(title) {
                for (side in 0..1) {
                    val slot = g * 2 + side
                    val opts = when (g) { 0 -> SINGLE_FUNCS; 3 -> LONG_MODES; else -> MULTI_FUNCS }
                    Picker(if (side == 0) "Esquerdo" else "Direito", opts, s.gestures.getOrNull(slot), on) {
                        BudsManager.setGesture(slot, it)
                    }
                }
                if (g == 3) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Ciclo do toque longo", color = Muted, fontSize = 12.sp)
                    Picker("Esquerdo", LONG_CYCLE, s.longGestureSettings?.first, on) { BudsManager.setLongGestureSettings(true, it) }
                    Picker("Direito", LONG_CYCLE, s.longGestureSettings?.second, on) { BudsManager.setLongGestureSettings(false, it) }
                }
            }
        }
    }
}

@Composable
private fun ConnectionTab(s: BudsState, pickDevice: () -> Unit) {
    val ctx = LocalContext.current
    val on = s.link == Link.CONNECTED
    var auto by remember { mutableStateOf(Prefs.autoConnect(ctx)) }
    Scroll {
        Section("Dispositivo", s.deviceAddress) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = pickDevice, modifier = Modifier.weight(1f)) { Text("Escolher fone") }
                OutlinedButton(onClick = {
                    BudsManager.disconnect(); BudsService.send(ctx, BudsService.ACTION_CONNECT)
                }, modifier = Modifier.weight(1f)) { Text("Reconectar") }
            }
            s.firmware?.let { Text("Firmware: $it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            Toggle("Reconectar automaticamente", "Abre o canal de controle sempre que o fone ligar", auto) {
                auto = it; Prefs.setAutoConnect(ctx, it)
            }
        }
        Section("Estabilidade", "Ajustes que mais reduzem cortes e quedas") {
            Toggle(
                "Conexão dupla (2 aparelhos)",
                "Desligar libera banda de rádio para um só celular: menos cortes e menor latência.",
                s.dualConnection, on
            ) { BudsManager.setDualConnection(it) }
            Spacer(Modifier.height(8.dp))
            Tip("1", "Nas Opções do desenvolvedor, em \"Codec de áudio Bluetooth\", teste AAC (mais qualidade) ou SBC (mais estável em lugares com muita interferência).")
            Tip("2", "Ative \"Desativar volume absoluto\" se o volume oscilar ou ficar baixo demais.")
            Tip("3", "No Xiaomi/Redmi: Segurança → Permissões → Início automático → ative o Buds Control, e tire a restrição de bateria do app.")
            Tip("4", "Mantenha o celular do mesmo lado do fone principal; o corpo bloqueia o sinal de 2,4 GHz.")
            Tip("5", "Atualize o firmware pelo app oficial Xiaomi Earbuds: correções de conexão vêm por lá.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open(ctx, Settings.ACTION_BLUETOOTH_SETTINGS) }, modifier = Modifier.weight(1f)) {
                    Text("Bluetooth", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { open(ctx, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }, modifier = Modifier.weight(1f)) {
                    Text("Desenvolvedor", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { open(ctx, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }, modifier = Modifier.weight(1f)) {
                    Text("Bateria", fontSize = 12.sp)
                }
            }
        }
        Section("Histórico de conexão", "Quedas registradas enquanto o app roda") {
            if (s.connectionEvents.isEmpty()) Text("Nenhum evento ainda.", color = Muted, fontSize = 12.sp)
            s.connectionEvents.take(30).forEach { Text(it, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun Tip(n: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("$n.", color = Orange, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

private fun open(ctx: Context, action: String) {
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(ctx, "Tela não disponível neste aparelho", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun AdvancedTab(s: BudsState) {
    val ctx = LocalContext.current
    val on = s.link == Link.CONNECTED
    Scroll {
        SmartAncSettings()
        ProfilesSection(s)
        Section("Console do protocolo", "Envia comandos crus ao fone, para testar recursos não mapeados. Use com cuidado.") {
            var op by remember { mutableStateOf("08") }
            var payload by remember { mutableStateOf("02 04 01") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(op, { op = it }, label = { Text("Opcode") }, singleLine = true, modifier = Modifier.width(100.dp))
                OutlinedTextField(payload, { payload = it }, label = { Text("Payload (hex)") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                runCatching { BudsManager.sendRaw(op.trim().toInt(16), payload.hexToBytes()) }
                    .onFailure { Toast.makeText(ctx, "Hex inválido: ${it.message}", Toast.LENGTH_SHORT).show() }
            }, enabled = on, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Enviar") }
            OutlinedButton(onClick = { BudsManager.refreshAll() }, enabled = on, modifier = Modifier.fillMaxWidth()) {
                Text("Reler tudo do fone")
            }
        }
        Section("Registro (log)") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("buds-log", s.log.joinToString("\n")))
                    Toast.makeText(ctx, "Log copiado", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f)) { Text("Copiar log") }
                OutlinedButton(onClick = { BudsManager.update { it.copy(log = emptyList()) } }, modifier = Modifier.weight(1f)) { Text("Limpar") }
            }
            Spacer(Modifier.height(8.dp))
            s.log.takeLast(80).reversed().forEach {
                Text(it, color = Color(0xFFB8C2CF), fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
private fun SmartAncSettings() {
    val ctx = LocalContext.current
    var c by remember { mutableStateOf(SmartAnc.config) }
    Section("Ajustes do ANC Inteligente") {
        LabeledSlider("Leve abaixo de", c.lightBelowDb, 30f..70f, "dB") { c = c.copy(lightBelowDb = it) }
        LabeledSlider("Profundo acima de", c.deepAboveDb, 45f..90f, "dB") { c = c.copy(deepAboveDb = it) }
        LabeledSlider("Calibração do microfone", c.calibrationDb, -20f..20f, "dB") { c = c.copy(calibrationDb = it) }
        LabeledSlider("Medir a cada", c.intervalSec.toFloat(), 3f..60f, "s") { c = c.copy(intervalSec = it.toInt()) }
        LabeledSlider("Tempo mínimo entre trocas", c.holdSec.toFloat(), 5f..120f, "s") { c = c.copy(holdSec = it.toInt()) }
        Button(onClick = {
            val fixed = if (c.deepAboveDb <= c.lightBelowDb + 4) c.copy(deepAboveDb = c.lightBelowDb + 4) else c
            c = fixed
            SmartAnc.saveConfig(ctx, fixed)
            Toast.makeText(ctx, "Ajustes salvos", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Salvar ajustes") }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("%.0f %s".format(value, unit), color = Muted, fontSize = 13.sp)
        }
        Slider(value = value, onValueChange = { onChange(Math.round(it).toFloat()) }, valueRange = range)
    }
}

@Composable
private fun ProfilesSection(s: BudsState) {
    val ctx = LocalContext.current
    val on = s.link == Link.CONNECTED
    var profiles by remember { mutableStateOf(Prefs.profiles(ctx)) }
    var name by remember { mutableStateOf("") }
    Section("Perfis", "Salva modo de som, intensidade e EQ atuais para aplicar com um toque (ex.: Ônibus, Academia, Dormir).") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.replace("|", "") }, label = { Text("Nome") }, singleLine = true,
                modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
            Button(onClick = {
                if (name.isNotBlank()) {
                    val data = (listOf(s.ambientMode ?: 1, s.ancStrength ?: 0, s.transparencyStrength ?: 0, s.eqPreset ?: 0) + s.eqBands)
                        .joinToString(",")
                    Prefs.saveProfile(ctx, name.trim(), data); profiles = Prefs.profiles(ctx); name = ""
                }
            }) { Text("Salvar") }
        }
        profiles.forEach { (n, data) ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(n, color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = { applyProfile(data) }, enabled = on) { Text("Aplicar") }
                TextButton(onClick = { Prefs.deleteProfile(ctx, n); profiles = Prefs.profiles(ctx) }) { Text("Apagar", color = Muted) }
            }
        }
    }
}

private fun applyProfile(data: String) {
    val v = data.split(",").mapNotNull { it.trim().toIntOrNull() }
    if (v.size < 14) return
    BudsManager.setAmbientMode(v[0])
    if (v[0] == 1) BudsManager.setAncStrength(v[1])
    if (v[0] == 2) BudsManager.setTransparencyStrength(v[2])
    if (v[3] == BudsManager.EQ_CUSTOM) BudsManager.setCustomEq(v.subList(4, 14)) else BudsManager.setEqPreset(v[3])
}

@SuppressLint("MissingPermission")
@Composable
private fun DevicePicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val devices = remember {
        runCatching {
            ctx.getSystemService(BluetoothManager::class.java).adapter.bondedDevices
                .map { (it.name ?: it.address) to it.address }
                .sortedByDescending { (n, _) -> n.contains("buds", ignoreCase = true) || n.contains("redmi", ignoreCase = true) }
        }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        title = { Text("Escolha seus fones") },
        text = {
            if (devices.isEmpty()) Text("Nenhum aparelho pareado. Pareie os Redmi Buds nas configurações de Bluetooth primeiro.")
            else LazyColumn {
                items(devices) { (n, a) ->
                    TextButton(onClick = { onPick(a) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(n, color = if (n.contains("buds", true)) Orange else Color.White)
                            Text(a, color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    )
}
