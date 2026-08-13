package org.quickping.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.quickping.app.MainActivity
import org.quickping.app.R

class QuickPingVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopping = AtomicBoolean(false)
    private var tunnelJob: Job? = null
    private lateinit var core: TunnelCore

    override fun onCreate() {
        super.onCreate()
        val platform = AndroidSingBoxPlatform(this)
        core = SingBoxTunnelCore(platform) { disconnect() }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> disconnect()
            ACTION_CONNECT -> connect(intent.getStringExtra(EXTRA_CONFIG_JSON).orEmpty())
        }
        return START_NOT_STICKY
    }

    private fun connect(configJson: String) {
        if (state.value == ServiceState.Connecting || state.value == ServiceState.Connected) return
        stopping.set(false)
        startForeground(NOTIFICATION_ID, connectionNotification())
        state.value = ServiceState.Connecting
        tunnelJob = serviceScope.launch {
            runCatching {
                require(configJson.isNotBlank()) { "Missing runtime configuration" }
                core.start(configJson)
                state.value = ServiceState.Connected
                getSystemService<NotificationManager>()?.notify(
                    NOTIFICATION_ID,
                    connectionNotification(connected = true),
                )
            }.onFailure { error ->
                if (error is CancellationException && stopping.get()) return@onFailure
                runCatching { core.stop() }
                state.value = ServiceState.Error
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun disconnect() {
        if (!stopping.compareAndSet(false, true)) return
        tunnelJob?.cancel()
        tunnelJob = serviceScope.launch {
            runCatching { core.stop() }
            state.value = ServiceState.Disconnected
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun connectionNotification(connected: Boolean = false): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this,
            1,
            Intent(this, QuickPingVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tray_connected)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                if (connected) getString(R.string.vpn_connected) else getString(R.string.vpn_connecting),
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "قطع اتصال", disconnect)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onRevoke() {
        disconnect()
        super.onRevoke()
    }

    override fun onDestroy() {
        if (::core.isInitialized && core.isRunning()) {
            runCatching { runBlocking(Dispatchers.IO) { core.stop() } }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    companion object {
        const val ACTION_CONNECT = "org.quickping.action.CONNECT"
        const val ACTION_DISCONNECT = "org.quickping.action.DISCONNECT"
        const val EXTRA_CONFIG_JSON = "runtime_config_json"
        private const val CHANNEL_ID = "quickping_vpn"
        private const val NOTIFICATION_ID = 2401
        val state = MutableStateFlow(ServiceState.Disconnected)
    }
}

enum class ServiceState { Disconnected, Connecting, Connected, Error }
