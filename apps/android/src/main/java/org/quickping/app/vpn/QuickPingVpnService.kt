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
import android.util.Log
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
import org.quickping.app.data.settings.QuickPingSettingsStore

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
            ACTION_CONNECT -> connect()
        }
        return START_NOT_STICKY
    }

    private fun connect() {
        if (status.value.state == ServiceState.Connecting || status.value.state == ServiceState.Connected) return
        stopping.set(false)
        startForeground(NOTIFICATION_ID, connectionNotification())
        publish(ServiceState.Connecting)
        tunnelJob = serviceScope.launch {
            runCatching {
                val rawConfig = VpnRuntimeStore(this@QuickPingVpnService).read()
                val settingsStore = QuickPingSettingsStore(this@QuickPingVpnService)
                val compiled = VpnConfigCompiler.compile(
                    rawConfigJson = rawConfig,
                    settings = settingsStore.load(),
                    enabledGuardianCategories = settingsStore.enabledGuardianCategoryIds(),
                    applicationPackage = packageName,
                )
                core.start(compiled.configJson, compiled.launchOptions)
                publish(ServiceState.Connected)
                getSystemService<NotificationManager>()?.notify(
                    NOTIFICATION_ID,
                    connectionNotification(connected = true),
                )
            }.onFailure { error ->
                if (error is CancellationException && stopping.get()) return@onFailure
                runCatching { core.stop() }
                val failure = error.toVpnFailure()
                Log.e(TAG, "VPN startup failed [${failure.code}]: ${failure.safeDetail}")
                publish(ServiceState.Error, failure)
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
            publish(ServiceState.Disconnected)
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

    private fun publish(state: ServiceState, failure: VpnFailure? = null) {
        status.value = VpnServiceStatus(state = state, failure = failure)
    }

    companion object {
        const val ACTION_CONNECT = "org.quickping.action.CONNECT"
        const val ACTION_DISCONNECT = "org.quickping.action.DISCONNECT"
        private const val CHANNEL_ID = "quickping_vpn"
        private const val NOTIFICATION_ID = 2401
        val status = MutableStateFlow(VpnServiceStatus(ServiceState.Disconnected))
        private const val TAG = "QuickPingVpnService"
    }
}

enum class ServiceState { Disconnected, Connecting, Connected, Error }

data class VpnFailure(
    val code: String,
    val userMessage: String,
    val safeDetail: String,
)

data class VpnServiceStatus(
    val state: ServiceState,
    val failure: VpnFailure? = null,
)

private fun Throwable.toVpnFailure(): VpnFailure {
    val source = generateSequence(this) { it.cause }.toList()
    val detail = source.mapNotNull { it.message }.firstOrNull { it.isNotBlank() }.orEmpty()
    val normalized = detail.lowercase()
    val (code, message) = when {
        "vpn permission" in normalized -> "vpn_permission" to "مجوز VPN داده نشده است"
        "no saved vpn configuration" in normalized || "missing runtime" in normalized ->
            "missing_config" to "پیکربندی اتصال ذخیره نشده است"
        "too large" in normalized -> "config_too_large" to "حجم پیکربندی سرور بیش از حد مجاز است"
        "no usable proxy outbound" in normalized || "no usable outbound" in normalized ->
            "missing_outbound" to "پیکربندی سرور مسیر خروجی قابل استفاده ندارد"
        "parse" in normalized || "json" in normalized || "configuration" in normalized || "config" in normalized ->
            "invalid_config" to "پیکربندی سرور با هستهٔ VPN سازگار نیست"
        "dns" in normalized -> "dns" to "راه‌اندازی DNS تونل ناموفق بود"
        "certificate" in normalized || "tls" in normalized -> "tls" to "اعتبارسنجی امنیتی سرور ناموفق بود"
        "timeout" in normalized || "network" in normalized || "connect" in normalized ->
            "network" to "ارتباط با سرور برقرار نشد"
        else -> "core_start" to "هستهٔ VPN راه‌اندازی نشد"
    }
    val safeDetail = detail
        .replace(Regex("https?://\\S+"), "[url]")
        .replace(Regex("(?i)(password|token|uuid|secret)[=: ]+\\S+"), "\$1=[redacted]")
        .take(240)
        .ifBlank { this::class.java.simpleName }
    return VpnFailure(code, message, safeDetail)
}
