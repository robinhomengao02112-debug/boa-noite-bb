package com.budscontrol.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class BudsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val saved = Prefs.deviceAddress(ctx)
            if (dev == null || dev.address != saved) return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    BudsManager.connectionEvent("Fone conectou ao celular (ACL)")
                    if (Prefs.autoConnect(ctx)) scope.launch {
                        delay(2500) // dá tempo ao A2DP/HFP de subir antes do canal de controle
                        BudsManager.connect(dev)
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    BudsManager.connectionEvent("Fone desconectou do celular (ACL)")
                    BudsManager.disconnect()
                }
                ACTION_BATTERY_LEVEL_CHANGED -> {
                    val lvl = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                    if (lvl >= 0) BudsManager.update { it.copy(systemBattery = lvl) }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(withMic = false)
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        SmartAnc.loadConfig(this)

        scope.launch {
            BudsManager.state
                .map { Triple(it.link, Triple(it.left, it.right, it.case), Triple(it.ambientMode, it.ancStrength, it.smartAncDecision)) }
                .distinctUntilChanged()
                .collect { notifyUpdate() }
        }
        tryConnectSaved()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> tryConnectSaved()
            ACTION_MODE_OFF -> BudsManager.setAmbientMode(0)
            ACTION_MODE_ANC -> BudsManager.setAmbientMode(1)
            ACTION_MODE_TRANSPARENCY -> BudsManager.setAmbientMode(2)
            ACTION_TURBO -> BudsManager.turboAnc()
            ACTION_SMART_ON -> {
                if (hasMic()) {
                    startInForeground(withMic = true)
                    SmartAnc.start()
                } else BudsManager.log("Permissão de microfone necessária para o ANC Inteligente")
            }
            ACTION_SMART_OFF -> {
                SmartAnc.stop()
                startInForeground(withMic = false)
            }
            ACTION_STOP -> {
                SmartAnc.stop()
                BudsManager.disconnect()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        SmartAnc.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun tryConnectSaved() {
        val addr = Prefs.deviceAddress(this) ?: return
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) { BudsManager.log("Bluetooth desligado"); return }
        val dev = runCatching { adapter.getRemoteDevice(addr) }.getOrNull() ?: return
        readSystemBattery(dev)
        BudsManager.connect(dev)
    }

    private fun readSystemBattery(dev: BluetoothDevice) {
        // API oculta, mas acessível: nível de bateria informado pelo perfil HFP.
        runCatching {
            val lvl = dev.javaClass.getMethod("getBatteryLevel").invoke(dev) as Int
            if (lvl >= 0) BudsManager.update { it.copy(systemBattery = lvl) }
        }
    }

    private fun startInForeground(withMic: Boolean) {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (withMic && hasMic()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            try {
                startForeground(NOTIF_ID, notif, type)
            } catch (e: Exception) {
                BudsManager.log("Falha ao iniciar serviço em primeiro plano: ${e.message}")
                runCatching { startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) }
            }
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Controle dos fones", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    private fun action(act: String, req: Int): PendingIntent =
        PendingIntent.getService(this, req, Intent(this, BudsService::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun buildNotification(): Notification {
        val s = BudsManager.state.value
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val bat = listOfNotNull(
            s.left?.let { "E ${it.level}%" }, s.right?.let { "D ${it.level}%" }, s.case?.let { "Estojo ${it.level}%" }
        ).joinToString(" · ")
        val mode = when (s.ambientMode) { 0 -> "Desligado"; 1 -> "Cancelamento"; 2 -> "Transparência"; else -> "—" }
        val title = if (s.link == Link.CONNECTED) "${s.deviceName ?: "Fones"} · $mode" else s.statusText
        val text = listOfNotNull(bat.ifEmpty { null }, s.smartAncDecision?.let { "Auto: $it" }).joinToString("  |  ")
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_buds)
            .setContentTitle(title)
            .setContentText(text.ifEmpty { "Toque para abrir" })
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "ANC", action(ACTION_MODE_ANC, 1)).build())
            .addAction(Notification.Action.Builder(null, "Transparência", action(ACTION_MODE_TRANSPARENCY, 2)).build())
            .addAction(Notification.Action.Builder(null, "Desligar", action(ACTION_MODE_OFF, 3)).build())
            .build()
    }

    private fun notifyUpdate() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    companion object {
        private const val CHANNEL = "buds"
        private const val NOTIF_ID = 42
        const val ACTION_CONNECT = "connect"
        const val ACTION_MODE_OFF = "mode_off"
        const val ACTION_MODE_ANC = "mode_anc"
        const val ACTION_MODE_TRANSPARENCY = "mode_transp"
        const val ACTION_TURBO = "turbo"
        const val ACTION_SMART_ON = "smart_on"
        const val ACTION_SMART_OFF = "smart_off"
        const val ACTION_STOP = "stop"

        private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

        fun send(ctx: Context, action: String) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, BudsService::class.java).setAction(action))
        }
    }
}

object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("buds", Context.MODE_PRIVATE)
    fun deviceAddress(ctx: Context): String? = p(ctx).getString("address", null)
    fun setDevice(ctx: Context, address: String) = p(ctx).edit().putString("address", address).apply()
    fun autoConnect(ctx: Context) = p(ctx).getBoolean("auto", true)
    fun setAutoConnect(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("auto", v).apply()

    fun profiles(ctx: Context): Map<String, String> =
        p(ctx).getStringSet("profiles", emptySet())!!.mapNotNull {
            val i = it.indexOf('|'); if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
        }.toMap()

    fun saveProfile(ctx: Context, name: String, data: String) {
        val cur = profiles(ctx).toMutableMap(); cur[name] = data
        p(ctx).edit().putStringSet("profiles", cur.map { "${it.key}|${it.value}" }.toSet()).apply()
    }

    fun deleteProfile(ctx: Context, name: String) {
        val cur = profiles(ctx).toMutableMap(); cur.remove(name)
        p(ctx).edit().putStringSet("profiles", cur.map { "${it.key}|${it.value}" }.toSet()).apply()
    }
}
