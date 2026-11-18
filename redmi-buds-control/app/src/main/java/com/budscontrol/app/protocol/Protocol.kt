/*
 * Protocolo de controle dos fones Xiaomi/Redmi (canal RFCOMM 0xFD2D).
 * Engenharia reversa original: projeto Gadgetbridge (AGPL v3). Este arquivo segue a mesma licença.
 *
 * Quadro: FE DC BA | tipo | opcode | tamanho(2, big-endian) | [status se resposta] | seq | payload | EF
 */
package com.budscontrol.app.protocol

import java.io.ByteArrayOutputStream

object MsgType {
    const val PHONE_REQUEST = 0xC4
    const val RESPONSE = 0x04
    const val EARBUDS_REQUEST = 0xC0
    const val EARBUDS_NOTIFY = 0xC7

    fun isRequest(type: Int) = (type and 0x40) != 0
}

object Op {
    const val GET_DEVICE_INFO = 0x02
    const val ANC = 0x08
    const val GET_DEVICE_RUN_INFO = 0x09
    const val REPORT_STATUS = 0x0E
    const val AUTH_CHALLENGE = 0x50
    const val AUTH_CONFIRM = 0x51
    const val SET_CONFIG = 0xF2
    const val GET_CONFIG = 0xF3
    const val NOTIFY_CONFIG = 0xF4
}

object Cfg {
    const val GESTURES = 0x02
    const val AUTO_ANSWER = 0x03
    const val DOUBLE_CONNECTION = 0x04
    const val EAR_DETECTION = 0x06
    const val EQ_PRESET = 0x07
    const val LONG_GESTURES = 0x0A
    const val EFFECT_STRENGTH = 0x0B
    const val ADAPTIVE_ANC = 0x25
    const val ADAPTIVE_SOUND = 0x29
    const val EQ_CURVE = 0x37
    const val CUSTOMIZED_ANC = 0x3B

    val ALL_READABLE = listOf(
        EFFECT_STRENGTH, ADAPTIVE_ANC, GESTURES, LONG_GESTURES, EAR_DETECTION,
        DOUBLE_CONNECTION, AUTO_ANSWER, ADAPTIVE_SOUND, EQ_PRESET, EQ_CURVE
    )
}

/** Alvo do ajuste de intensidade. */
object Strength {
    const val ANC = 0x01
    const val TRANSPARENCY = 0x02
}

class Message(val type: Int, val opcode: Int, val seq: Int, val payload: ByteArray) {

    fun encode(): ByteArray {
        val extra = if (MsgType.isRequest(type)) 1 else 2
        val len = payload.size + extra
        val out = ByteArrayOutputStream(payload.size + 8 + extra)
        out.write(0xFE); out.write(0xDC); out.write(0xBA)
        out.write(type); out.write(opcode)
        out.write((len shr 8) and 0xFF); out.write(len and 0xFF)
        if (!MsgType.isRequest(type)) out.write(0x00)
        out.write(seq and 0xFF)
        out.write(payload)
        out.write(0xEF)
        return out.toByteArray()
    }

    override fun toString(): String =
        "type=%02X op=%02X seq=%d payload=%s".format(type, opcode, seq, payload.toHex())

    companion object {
        /** Decodifica um quadro completo (cabeçalho ao terminador). */
        fun fromFrame(frame: ByteArray): Message {
            val type = frame[3].u
            val opcode = frame[4].u
            val payloadOffset = 3 + if (MsgType.isRequest(type)) 5 else 6
            val seq = frame[payloadOffset - 1].u
            val end = frame.size - 1
            val payload = if (end > payloadOffset) frame.copyOfRange(payloadOffset, end) else ByteArray(0)
            return Message(type, opcode, seq, payload)
        }
    }
}

/** Remonta quadros a partir de um fluxo de bytes que pode chegar fragmentado ou agrupado. */
class FrameAssembler {
    private val buf = ByteArrayOutputStream()

    fun feed(data: ByteArray, count: Int): List<Message> {
        buf.write(data, 0, count)
        val out = mutableListOf<Message>()
        var bytes = buf.toByteArray()
        var pos = 0
        while (true) {
            val start = findHeader(bytes, pos)
            if (start < 0) { pos = maxOf(pos, bytes.size - 2); break }
            if (bytes.size - start < 7) { pos = start; break }
            val len = (bytes[start + 5].u shl 8) or bytes[start + 6].u
            val total = len + 8
            if (len > 4096) { pos = start + 3; continue } // lixo: pula cabeçalho falso
            if (bytes.size - start < total) { pos = start; break }
            val frame = bytes.copyOfRange(start, start + total)
            runCatching { out += Message.fromFrame(frame) }
            pos = start + total
        }
        buf.reset()
        if (pos < bytes.size) buf.write(bytes, pos, bytes.size - pos)
        bytes = ByteArray(0)
        return out
    }

    private fun findHeader(b: ByteArray, from: Int): Int {
        var i = from
        while (i <= b.size - 3) {
            if (b[i].u == 0xFE && b[i + 1].u == 0xDC && b[i + 2].u == 0xBA) return i
            i++
        }
        return -1
    }
}

/** Fábrica de comandos. A sequência é controlada por quem envia. */
class Commands {
    private var seq = 0
    private fun next() = (seq++) and 0xFF

    private fun req(op: Int, vararg p: Int) =
        Message(MsgType.PHONE_REQUEST, op, next(), ByteArray(p.size) { p[it].toByte() }).encode()

    fun startAuth(): ByteArray {
        val rnd = SaferAuth.randomChallenge()
        val p = ByteArray(17); p[0] = 0x01; System.arraycopy(rnd, 0, p, 1, 16)
        return Message(MsgType.PHONE_REQUEST, Op.AUTH_CHALLENGE, next(), p).encode()
    }

    fun authConfirm() = req(Op.AUTH_CONFIRM, 0x01, 0x00)
    fun getDeviceInfo() = req(Op.GET_DEVICE_INFO, 0xFF, 0xFF, 0xFF, 0xFF)
    fun getRunInfo() = req(Op.GET_DEVICE_RUN_INFO, 0xFF, 0xFF, 0xFF, 0xFF)

    fun getAllConfig(): ByteArray {
        val out = ByteArrayOutputStream()
        Cfg.ALL_READABLE.forEach { out.write(req(Op.GET_CONFIG, 0x00, it)) }
        return out.toByteArray()
    }

    /** 0 = desligado, 1 = cancelamento de ruído, 2 = transparência. */
    fun setAmbientMode(mode: Int) = req(Op.ANC, 0x02, 0x04, mode)

    /** ANC: 0 equilibrado, 1 leve, 2 profundo. Transparência: 0 normal, 1 voz, 2 ambiente. */
    fun setStrength(target: Int, level: Int) = req(Op.SET_CONFIG, 0x04, 0x00, Cfg.EFFECT_STRENGTH, target, level)

    fun setBool(cfg: Int, on: Boolean) = req(Op.SET_CONFIG, 0x03, 0x00, cfg, if (on) 1 else 0)
    fun setInt(cfg: Int, value: Int) = req(Op.SET_CONFIG, 0x03, 0x00, cfg, value)

    /** Detecção de uso: no protocolo 0 = ligado. */
    fun setEarDetection(on: Boolean) = req(Op.ANC, 0x02, 0x06, if (on) 0 else 1)

    /** Gesto: tipo (single 4, double 1, triple 2, long 3) e lado. */
    fun setGesture(interaction: Int, left: Boolean, value: Int) =
        req(Op.SET_CONFIG, 0x05, 0x00, Cfg.GESTURES, interaction, if (left) value else 0xFF, if (left) 0xFF else value)

    fun setLongGestureMode(left: Boolean, value: Int) =
        req(Op.SET_CONFIG, 0x04, 0x00, Cfg.LONG_GESTURES, if (left) value else 0xFF, if (left) 0xFF else value)

    /** EQ personalizado de 10 bandas, valores em dB de -6 a +6. */
    fun setCustomEq(bandsDb: IntArray): ByteArray {
        val freqs = intArrayOf(62, 125, 250, 500, 1000, 2016, 4000, 8000, 12000, 16000)
        val p = mutableListOf(0x24, 0x00, Cfg.EQ_CURVE, 0x05, 0x01, 0x01, 0x0A)
        for (i in 0 until 10) {
            p += (freqs[i] shr 8) and 0xFF
            p += freqs[i] and 0xFF
            p += eqDbToByte(bandsDb[i])
        }
        return req(Op.SET_CONFIG, *p.toIntArray())
    }

    fun raw(op: Int, payload: ByteArray) = Message(MsgType.PHONE_REQUEST, op, next(), payload).encode()

    companion object {
        fun eqDbToByte(db: Int): Int {
            val v = db.coerceIn(-6, 6)
            return if (v >= 0) v else 128 - v
        }

        fun eqByteToDb(b: Int): Int = if (b and 0x80 != 0) -(b and 0x7F) else b
    }
}

val Byte.u: Int get() = toInt() and 0xFF

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

fun String.hexToBytes(): ByteArray {
    val clean = replace(" ", "").replace(":", "").replace("\n", "")
    require(clean.length % 2 == 0) { "Hex com número ímpar de dígitos" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
