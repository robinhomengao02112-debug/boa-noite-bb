package com.budscontrol.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * ANC Inteligente: mede o ruído ambiente com o microfone do celular e escolhe
 * a intensidade do cancelamento (leve / equilibrado / profundo) com histerese,
 * para não ficar trocando o tempo todo.
 */
object SmartAnc {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    data class Config(
        val lightBelowDb: Float = 50f,
        val deepAboveDb: Float = 66f,
        val calibrationDb: Float = 0f,
        val intervalSec: Int = 8,
        val holdSec: Int = 20,
    )

    @Volatile
    var config = Config()

    fun loadConfig(ctx: Context) {
        val p = ctx.getSharedPreferences("smart_anc", Context.MODE_PRIVATE)
        config = Config(
            lightBelowDb = p.getFloat("light", 50f),
            deepAboveDb = p.getFloat("deep", 66f),
            calibrationDb = p.getFloat("cal", 0f),
            intervalSec = p.getInt("interval", 8),
            holdSec = p.getInt("hold", 20),
        )
    }

    fun saveConfig(ctx: Context, c: Config) {
        config = c
        ctx.getSharedPreferences("smart_anc", Context.MODE_PRIVATE).edit()
            .putFloat("light", c.lightBelowDb).putFloat("deep", c.deepAboveDb)
            .putFloat("cal", c.calibrationDb).putInt("interval", c.intervalSec)
            .putInt("hold", c.holdSec).apply()
    }

    val running get() = job?.isActive == true

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        BudsManager.update { it.copy(smartAncOn = true) }
        BudsManager.log("ANC Inteligente ativado")
        job = scope.launch {
            var smoothed: Float? = null
            var lastChange = 0L
            while (isActive) {
                val db = measureOnce()
                if (db != null) {
                    val s = smoothed?.let { it * 0.6f + db * 0.4f } ?: db
                    smoothed = s
                    BudsManager.update { it.copy(ambientDb = s) }
                    decide(s, lastChange)?.let { lastChange = it }
                }
                delay(config.intervalSec * 1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        BudsManager.update { it.copy(smartAncOn = false, smartAncDecision = null) }
        BudsManager.log("ANC Inteligente desativado")
    }

    /** Retorna o horário da troca, se houve troca. */
    private fun decide(db: Float, lastChange: Long): Long? {
        val st = BudsManager.state.value
        if (!BudsManager.isConnected) return null
        val c = config
        val current = st.ancStrength
        val margin = 3f
        val target = when {
            db >= c.deepAboveDb -> BudsManager.ANC_DEEP
            db <= c.lightBelowDb -> BudsManager.ANC_LIGHT
            // zona intermediária com histerese: só sai do extremo se passar da margem
            current == BudsManager.ANC_DEEP && db > c.deepAboveDb - margin -> BudsManager.ANC_DEEP
            current == BudsManager.ANC_LIGHT && db < c.lightBelowDb + margin -> BudsManager.ANC_LIGHT
            else -> BudsManager.ANC_BALANCED
        }
        val label = when (target) {
            BudsManager.ANC_DEEP -> "Profundo"
            BudsManager.ANC_LIGHT -> "Leve"
            else -> "Equilibrado"
        }
        BudsManager.update { it.copy(smartAncDecision = "%.0f dB → %s".format(db, label)) }
        val now = System.currentTimeMillis()
        val needsAncOn = st.ambientMode != 1
        if (target == current && !needsAncOn) return null
        if (now - lastChange < c.holdSec * 1000L && !needsAncOn) return null
        if (needsAncOn) BudsManager.setAmbientMode(1)
        if (st.adaptiveAnc == true) BudsManager.setAdaptiveAnc(false)
        BudsManager.setAncStrength(target)
        BudsManager.log("ANC Inteligente: %.0f dB → %s".format(db, label))
        return now
    }

    /** Mede ~1 s de áudio e devolve o nível aproximado em dB SPL. */
    @SuppressLint("MissingPermission")
    private fun measureOnce(): Float? {
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return null
        fun open(source: Int): AudioRecord? = runCatching {
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate))
        }.getOrNull()?.let { r ->
            if (r.state == AudioRecord.STATE_INITIALIZED) r else { r.release(); null }
        }
        // UNPROCESSED evita o AGC/supressão do celular, que distorceriam a medida.
        val rec = try {
            open(MediaRecorder.AudioSource.UNPROCESSED) ?: open(MediaRecorder.AudioSource.MIC)
                ?: throw IllegalStateException("AudioRecord não inicializou")
        } catch (e: Exception) {
            BudsManager.log("Microfone indisponível: ${e.message}")
            return null
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return null }
        return try {
            rec.startRecording()
            val buf = ShortArray(rate / 10)
            // descarta os primeiros 200 ms (ruído de partida do microfone)
            repeat(2) { rec.read(buf, 0, buf.size) }
            var sum = 0.0
            var count = 0L
            repeat(10) {
                val n = rec.read(buf, 0, buf.size)
                for (k in 0 until maxOf(n, 0)) { val v = buf[k].toDouble(); sum += v * v }
                count += maxOf(n, 0)
            }
            if (count == 0L) return null
            val rms = sqrt(sum / count).coerceAtLeast(1.0)
            val dbfs = 20 * log10(rms / 32768.0)
            // Aproximação típica de microfone de celular: 0 dBFS ≈ 94 dB SPL + calibração do usuário.
            (dbfs + 94.0 + config.calibrationDb).toFloat()
        } catch (e: Exception) {
            BudsManager.log("Erro na medição: ${e.message}")
            null
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }
}
