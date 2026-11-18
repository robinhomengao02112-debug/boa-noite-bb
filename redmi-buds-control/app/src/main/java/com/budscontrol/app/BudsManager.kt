package com.budscontrol.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.budscontrol.app.protocol.Cfg
import com.budscontrol.app.protocol.Commands
import com.budscontrol.app.protocol.FrameAssembler
import com.budscontrol.app.protocol.Message
import com.budscontrol.app.protocol.MsgType
import com.budscontrol.app.protocol.Op
import com.budscontrol.app.protocol.SaferAuth
import com.budscontrol.app.protocol.Strength
import com.budscontrol.app.protocol.toHex
import com.budscontrol.app.protocol.u
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class Link { DISCONNECTED, CONNECTING, AUTHENTICATING, CONNECTED, ERROR }

data class Battery(val level: Int, val charging: Boolean)

data class BudsState(
    val link: Link = Link.DISCONNECTED,
    val statusText: String = "Desconectado",
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val firmware: String? = null,
    val left: Battery? = null,
    val right: Battery? = null,
    val case: Battery? = null,
    val systemBattery: Int? = null,
    /** 0 desligado, 1 ANC, 2 transparência */
    val ambientMode: Int? = null,
    /** 0 equilibrado, 1 leve, 2 profundo */
    val ancStrength: Int? = null,
    /** 0 normal, 1 voz, 2 ambiente */
    val transparencyStrength: Int? = null,
    val adaptiveAnc: Boolean? = null,
    val adaptiveSound: Boolean? = null,
    val earDetection: Boolean? = null,
    val dualConnection: Boolean? = null,
    val autoAnswer: Boolean? = null,
    val eqPreset: Int? = null,
    val eqBands: List<Int> = List(10) { 0 },
    /** [single L, single R, double L, double R, triple L, triple R, long L, long R] */
    val gestures: List<Int?> = List(8) { null },
    val longGestureSettings: Pair<Int, Int>? = null,
    val inEar: String? = null,
    val smartAncOn: Boolean = false,
    val ambientDb: Float? = null,
    val smartAncDecision: String? = null,
    val connectionEvents: List<String> = emptyList(),
    val log: List<String> = emptyList(),
)

@SuppressLint("MissingPermission")
object BudsManager {
    private const val TAG = "BudsManager"
    private val UUID_CTRL: UUID = UUID.fromString("0000fd2d-0000-1000-8000-00805f9b34fb")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(BudsState())
    val state: StateFlow<BudsState> = _state.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var readJob: Job? = null
    private var connectJob: Job? = null
    private var cmd = Commands()
    private val auth by lazy { SaferAuth() }
    private val writeLock = Any()

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(line: String) {
        Log.d(TAG, line)
        val stamped = "${timeFmt.format(Date())}  $line"
        _state.update { it.copy(log = (it.log + stamped).takeLast(300)) }
    }

    fun connectionEvent(line: String) {
        val stamped = "${SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())}  $line"
        _state.update { it.copy(connectionEvents = (listOf(stamped) + it.connectionEvents).take(100)) }
        log("[conexão] $line")
    }

    fun update(block: (BudsState) -> BudsState) = _state.update(block)

    val isConnected get() = _state.value.link == Link.CONNECTED

    fun connect(device: BluetoothDevice) {
        if (connectJob?.isActive == true) return
        if (isConnected && _state.value.deviceAddress == device.address) return
        closeSocket()
        connectJob = scope.launch {
            val name = runCatching { device.name }.getOrNull() ?: device.address
            _state.update {
                it.copy(link = Link.CONNECTING, statusText = "Conectando a $name…", deviceName = name, deviceAddress = device.address)
            }
            var attempt = 0
            while (isActive && attempt < 3) {
                attempt++
                try {
                    openSocket(device)
                    return@launch
                } catch (e: IOException) {
                    log("Tentativa $attempt falhou: ${e.message}")
                    closeSocket()
                    delay(1500L * attempt)
                }
            }
            _state.update {
                it.copy(
                    link = Link.ERROR,
                    statusText = "Não foi possível abrir o canal de controle. Confirme que o fone está conectado ao celular e feche o app Xiaomi Earbuds, se estiver aberto."
                )
            }
        }
    }

    private fun openSocket(device: BluetoothDevice) {
        log("Abrindo RFCOMM ${UUID_CTRL} em ${device.address}")
        val s = try {
            device.createRfcommSocketToServiceRecord(UUID_CTRL).also { it.connect() }
        } catch (e: IOException) {
            log("Canal seguro falhou (${e.message}); tentando canal inseguro")
            device.createInsecureRfcommSocketToServiceRecord(UUID_CTRL).also { it.connect() }
        }
        socket = s
        output = s.outputStream
        cmd = Commands()
        _state.update { it.copy(link = Link.AUTHENTICATING, statusText = "Autenticando…") }
        connectionEvent("Canal de controle aberto")
        startReader(s)
        send(cmd.startAuth(), "AUTH start")
    }

    private fun startReader(s: BluetoothSocket) {
        readJob?.cancel()
        readJob = scope.launch {
            val input = s.inputStream
            val buf = ByteArray(4096)
            val assembler = FrameAssembler()
            try {
                while (isActive) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    for (m in assembler.feed(buf, n)) {
                        log("⇐ $m")
                        runCatching { handle(m) }.onFailure { log("Erro ao tratar mensagem: ${it.message}") }
                    }
                }
            } catch (e: IOException) {
                log("Leitura encerrada: ${e.message}")
            }
            if (socket === s) {
                closeSocket()
                connectionEvent("Canal de controle caiu")
                _state.update { it.copy(link = Link.DISCONNECTED, statusText = "Desconectado") }
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        closeSocket()
        _state.update { it.copy(link = Link.DISCONNECTED, statusText = "Desconectado") }
    }

    private fun closeSocket() {
        val s = socket
        socket = null
        output = null
        runCatching { s?.close() }
        readJob?.cancel()
    }

    private fun send(bytes: ByteArray, label: String = "") {
        val out = output ?: run { log("Sem conexão; comando ignorado ($label)"); return }
        try {
            synchronized(writeLock) {
                out.write(bytes)
                out.flush()
            }
            log("⇒ ${if (label.isNotEmpty()) "$label: " else ""}${bytes.toHex()}")
        } catch (e: IOException) {
            log("Falha ao enviar: ${e.message}")
            closeSocket()
            _state.update { it.copy(link = Link.DISCONNECTED, statusText = "Conexão perdida") }
        }
    }

    // ------------------------------------------------------------------ recebimento

    private fun handle(m: Message) {
        when (m.opcode) {
            Op.AUTH_CHALLENGE -> {
                if (m.type == MsgType.RESPONSE) {
                    send(cmd.authConfirm(), "AUTH confirm")
                } else {
                    val challenge = m.payload.copyOfRange(1, 17)
                    val resp = auth.respond(challenge)
                    val p = ByteArray(17); p[0] = 1; System.arraycopy(resp, 0, p, 1, 16)
                    send(Message(MsgType.RESPONSE, Op.AUTH_CHALLENGE, m.seq, p).encode(), "AUTH response")
                }
            }
            Op.AUTH_CONFIRM -> {
                if (m.type != MsgType.RESPONSE) {
                    send(Message(MsgType.RESPONSE, Op.AUTH_CONFIRM, m.seq, byteArrayOf(1)).encode(), "AUTH final")
                    _state.update { it.copy(link = Link.CONNECTED, statusText = "Conectado") }
                    connectionEvent("Autenticado com sucesso")
                    refreshAll()
                }
            }
            Op.GET_DEVICE_INFO -> decodeDeviceInfo(m.payload)
            Op.GET_DEVICE_RUN_INFO -> decodeRunInfo(m.payload)
            Op.REPORT_STATUS -> {
                decodeReport(m.payload)
                if (m.type != MsgType.RESPONSE)
                    send(Message(MsgType.RESPONSE, Op.REPORT_STATUS, m.seq, ByteArray(0)).encode(), "ack status")
            }
            Op.GET_CONFIG -> if (m.type == MsgType.RESPONSE) decodeConfig(m.payload)
            Op.NOTIFY_CONFIG -> {
                decodeNotify(m.payload)
                if (m.type != MsgType.RESPONSE)
                    send(Message(MsgType.RESPONSE, Op.NOTIFY_CONFIG, m.seq, ByteArray(0)).encode(), "ack notify")
            }
        }
    }

    private fun battery(b: Int): Battery? = if (b == 0xFF) null else Battery(b and 0x7F, b and 0x80 != 0)

    private inline fun forEachTlv(p: ByteArray, block: (len: Int, idx: Int, at: Int) -> Unit) {
        var i = 0
        while (i + 1 < p.size) {
            val len = p[i].u
            if (len == 0) break
            block(len, p[i + 1].u, i)
            i += len + 1
        }
    }

    private fun ByteArray.at(i: Int): Int? = if (i in indices) this[i].u else null

    private fun decodeDeviceInfo(p: ByteArray) {
        forEachTlv(p) { _, idx, i ->
            when (idx) {
                0x01 -> if (i + 5 < p.size) {
                    val fw = (0 until 2).joinToString(".") { k ->
                        val b = p[i + 2 + k].u; "${b shr 4}.${b and 0xF}"
                    }
                    _state.update { it.copy(firmware = fw) }
                }
                0x07 -> if (i + 4 < p.size) {
                    _state.update {
                        it.copy(left = battery(p[i + 2].u), right = battery(p[i + 3].u), case = battery(p[i + 4].u))
                    }
                }
            }
        }
    }

    private fun decodeRunInfo(p: ByteArray) {
        forEachTlv(p) { _, idx, i ->
            when (idx) {
                0x09 -> p.at(i + 2)?.let { v -> _state.update { it.copy(ambientMode = v) } }
                0x0A -> p.at(i + 2)?.let { v -> _state.update { it.copy(earDetection = v == 0) } }
            }
        }
    }

    private fun decodeReport(p: ByteArray) {
        forEachTlv(p) { _, idx, i ->
            when (idx) {
                0x00 -> if (i + 4 < p.size) _state.update {
                    it.copy(left = battery(p[i + 2].u), right = battery(p[i + 3].u), case = battery(p[i + 4].u))
                }
                0x04 -> p.at(i + 2)?.let { v -> _state.update { it.copy(ambientMode = v) } }
            }
        }
    }

    private fun decodeNotify(p: ByteArray) {
        var i = 0
        while (i + 2 < p.size) {
            val len = p[i].u
            if (len == 0) break
            when (p[i + 2].u) {
                0x0C -> p.at(i + 3)?.let { v ->
                    val wl = v and 0x08 != 0; val wr = v and 0x04 != 0
                    val cl = v and 0x02 != 0; val cr = v and 0x01 != 0
                    val txt = "E: ${if (wl) "no ouvido" else if (cl) "no estojo" else "fora"} · D: ${if (wr) "no ouvido" else if (cr) "no estojo" else "fora"}"
                    _state.update { it.copy(inEar = txt) }
                }
                0x0B -> {
                    val mode = p.at(i + 3); val lvl = p.at(i + 4)
                    if (mode != null && lvl != null) _state.update {
                        if (mode == Strength.ANC) it.copy(ancStrength = lvl) else it.copy(transparencyStrength = lvl)
                    }
                }
            }
            i += len + 1
        }
    }

    private fun decodeConfig(p: ByteArray) {
        val id = p.at(2) ?: return
        when (id) {
            Cfg.GESTURES -> _state.update {
                it.copy(gestures = listOf(4, 5, 7, 8, 10, 11, 13, 14).map { k -> p.at(k) })
            }
            Cfg.LONG_GESTURES -> {
                val l = p.at(3); val r = p.at(4)
                if (l != null && r != null) _state.update { it.copy(longGestureSettings = l to r) }
            }
            Cfg.AUTO_ANSWER -> p.at(3)?.let { v -> _state.update { it.copy(autoAnswer = v == 1) } }
            Cfg.DOUBLE_CONNECTION -> p.at(3)?.let { v -> _state.update { it.copy(dualConnection = v == 1) } }
            Cfg.EQ_PRESET -> p.at(3)?.let { v -> _state.update { it.copy(eqPreset = v) } }
            Cfg.ADAPTIVE_ANC -> p.at(3)?.let { v -> _state.update { it.copy(adaptiveAnc = v == 1) } }
            Cfg.ADAPTIVE_SOUND -> p.at(3)?.let { v -> _state.update { it.copy(adaptiveSound = v == 1) } }
            Cfg.EFFECT_STRENGTH -> {
                val target = p.at(3); val lvl = p.at(4)
                if (target != null && lvl != null) _state.update {
                    if (target == Strength.ANC) it.copy(ancStrength = lvl) else it.copy(transparencyStrength = lvl)
                }
            }
            Cfg.EQ_CURVE -> {
                val bands = (0 until 10).map { k -> p.at(12 + 3 * k)?.let(Commands::eqByteToDb) ?: 0 }
                _state.update { it.copy(eqBands = bands) }
            }
            else -> log("Config 0x%02X não tratada: %s".format(id, p.toHex()))
        }
    }

    // ------------------------------------------------------------------ comandos

    fun refreshAll() {
        send(cmd.getDeviceInfo(), "device info")
        send(cmd.getRunInfo(), "run info")
        send(cmd.getAllConfig(), "configs")
    }

    fun setAmbientMode(mode: Int) {
        send(cmd.setAmbientMode(mode), "modo ambiente $mode")
        _state.update { it.copy(ambientMode = mode) }
    }

    fun setAncStrength(level: Int) {
        send(cmd.setStrength(Strength.ANC, level), "intensidade ANC $level")
        _state.update { it.copy(ancStrength = level) }
    }

    fun setTransparencyStrength(level: Int) {
        send(cmd.setStrength(Strength.TRANSPARENCY, level), "transparência $level")
        _state.update { it.copy(transparencyStrength = level) }
    }

    fun setAdaptiveAnc(on: Boolean) {
        send(cmd.setBool(Cfg.ADAPTIVE_ANC, on), "ANC adaptativo $on")
        _state.update { it.copy(adaptiveAnc = on) }
    }

    fun setAdaptiveSound(on: Boolean) {
        send(cmd.setBool(Cfg.ADAPTIVE_SOUND, on), "som adaptativo $on")
        _state.update { it.copy(adaptiveSound = on) }
    }

    fun setDualConnection(on: Boolean) {
        send(cmd.setBool(Cfg.DOUBLE_CONNECTION, on), "conexão dupla $on")
        _state.update { it.copy(dualConnection = on) }
    }

    fun setAutoAnswer(on: Boolean) {
        send(cmd.setBool(Cfg.AUTO_ANSWER, on), "atender automático $on")
        _state.update { it.copy(autoAnswer = on) }
    }

    fun setEarDetection(on: Boolean) {
        send(cmd.setEarDetection(on), "detecção de uso $on")
        _state.update { it.copy(earDetection = on) }
    }

    fun setEqPreset(preset: Int) {
        send(cmd.setInt(Cfg.EQ_PRESET, preset), "EQ preset $preset")
        _state.update { it.copy(eqPreset = preset) }
    }

    fun setCustomEq(bands: List<Int>) {
        send(cmd.setInt(Cfg.EQ_PRESET, EQ_CUSTOM), "EQ preset personalizado")
        send(cmd.setCustomEq(bands.toIntArray()), "EQ curva")
        _state.update { it.copy(eqPreset = EQ_CUSTOM, eqBands = bands) }
    }

    /** slot: 0..7 na mesma ordem de [BudsState.gestures]. */
    fun setGesture(slot: Int, value: Int) {
        val interaction = intArrayOf(0x04, 0x01, 0x02, 0x03)[slot / 2]
        val left = slot % 2 == 0
        send(cmd.setGesture(interaction, left, value), "gesto $slot=$value")
        _state.update { s -> s.copy(gestures = s.gestures.toMutableList().also { it[slot] = value }) }
    }

    fun setLongGestureSettings(left: Boolean, value: Int) {
        send(cmd.setLongGestureMode(left, value), "toque longo ${if (left) "E" else "D"}=$value")
        _state.update { s ->
            val cur = s.longGestureSettings ?: (7 to 7)
            s.copy(longGestureSettings = if (left) value to cur.second else cur.first to value)
        }
    }

    fun sendRaw(op: Int, payload: ByteArray) = send(cmd.raw(op, payload), "manual")

    /** Isolamento máximo que o hardware permite. */
    fun turboAnc() {
        setAmbientMode(1)
        setAdaptiveAnc(false)
        setAncStrength(ANC_DEEP)
        log("Modo Turbo: ANC ligado, adaptativo desligado, intensidade profunda")
    }

    const val EQ_CUSTOM = 10
    const val ANC_BALANCED = 0
    const val ANC_LIGHT = 1
    const val ANC_DEEP = 2
}
