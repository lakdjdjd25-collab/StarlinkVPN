package org.quickping.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private lateinit var platform: AndroidSingBoxPlatform
    private lateinit var core: TunnelCore
    private lateinit var trafficMonitor: VpnTrafficMonitor

    override fun onCreate() {
        super.onCreate()
        platform = AndroidSingBoxPlatform(this)
        core = SingBoxTunnelCore(platform) { disconnect() }
        trafficMonitor = VpnTrafficMonitor()
        serviceScope.launch {
            trafficMonitor.stats.collect { traffic ->
                val current = status.value
                if (current.traffic != traffic) status.value = current.copy(traffic = traffic)
            }
        }
        // MutableStateFlow lives at process scope. If Android destroyed a previous
        // service instance unexpectedly, never let a stale Connected/Connecting
        // value survive into a fresh service that has no running native core.
        if (status.value.state == ServiceState.Connected || status.value.state == ServiceState.Connecting) {
            publish(ServiceState.Disconnected)
        }
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
        val currentState = status.value.state
        if (currentState == ServiceState.Connecting && tunnelJob?.isActive == true) return
        if (currentState == ServiceState.Connected && core.isRunning()) return
        if (currentState == ServiceState.Connecting || currentState == ServiceState.Connected) {
            // State without an active job/core is stale; recover instead of
            // short-circuiting and leaving a visual-only Connected state.
            publish(ServiceState.Disconnected)
        }

        stopping.set(false)
        startForeground(NOTIFICATION_ID, connectionNotification())
        publish(ServiceState.Connecting)
        tunnelJob = serviceScope.launch {
            runCatching {
                val rawConfig = VpnRuntimeStore(this@QuickPingVpnService).read()
                val settingsStore = QuickPingSettingsStore(this@QuickPingVpnService)
                val settings = settingsStore.load()
                val sanitizedConfig = VpnRuntimeSanitizer.sanitize(
                    rawConfigJson = rawConfig,
                    settings = settings,
                )
                val compiled = VpnConfigCompiler.compile(
                    rawConfigJson = sanitizedConfig,
                    settings = settings,
                    enabledGuardianCategories = settingsStore.enabledGuardianCategoryIds(),
                    applicationPackage = packageName,
                )
                val runtimeConfig = applyProviderDnsPolicy(
                    rawConfigJson = sanitizedConfig,
                    compiledConfigJson = compiled.configJson,
                    provider = settings.dnsProvider,
                )
                core.start(runtimeConfig, compiled.launchOptions)
                startTrafficMonitorWithRetry()

                // A running libbox process is not enough. In TUN mode, nimHUB's
                // own VpnService must have successfully established and retained
                // its ParcelFileDescriptor before any network probe can qualify
                // the connection as healthy.
                if (!settings.proxyModeEnabled) {
                    check(platform.hasTunInterface()) { "nimhub tun interface missing" }
                }

                // Record the native-core byte baseline before the verification
                // traffic. A successful HTTP request that bypasses sing-box must
                // not be allowed to promote the UI to Connected.
                val trafficBaseline = trafficMonitor.stats.value.totalBytes

                // TUN mode then proves Android VPN traffic with real HTTPS.
                // Proxy mode proves HTTPS through nimHUB's local HTTP proxy.
                val proxyVerificationPort = settings.proxyPort.takeIf { settings.proxyModeEnabled }
                check(
                    VpnConnectionVerifier(this@QuickPingVpnService).awaitHealthy(
                        proxyPort = proxyVerificationPort,
                    ),
                ) {
                    if (settings.proxyModeEnabled) "proxy verification failed" else "tunnel verification failed"
                }

                // The exact HTTPS verification traffic above must also be visible
                // in sing-box's own command-server counters. This closes the old
                // false-positive path where the app itself had internet but the
                // native tunnel carried zero bytes.
                check(trafficMonitor.awaitTrafficAfter(trafficBaseline)) {
                    "native traffic not observed after verification"
                }

                check(core.isRunning()) { "native core stopped during verification" }
                if (!settings.proxyModeEnabled) {
                    check(platform.hasTunInterface()) { "nimhub tun interface closed during verification" }
                }

                publish(ServiceState.Connected)
                getSystemService<NotificationManager>()?.notify(
                    NOTIFICATION_ID,
                    connectionNotification(connected = true),
                )
            }.onFailure { error ->
                if (error is CancellationException && stopping.get()) return@onFailure
                // Suppress the native serviceStop callback while we intentionally
                // tear a failed startup down; otherwise it can race this path and
                // overwrite Error with Disconnected.
                stopping.set(true)
                trafficMonitor.stop()
                runCatching { core.stop() }
                val failure = error.toVpnFailure()
                Log.e(TAG, "VPN startup failed [${failure.code}]: ${failure.safeDetail}")
                publish(ServiceState.Error, failure)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun startTrafficMonitorWithRetry() {
        var lastError: Throwable? = null
        repeat(4) { attempt ->
            val result = runCatching { trafficMonitor.start() }
            if (result.isSuccess) return
            lastError = result.exceptionOrNull()
            trafficMonitor.stop()
            if (attempt < 3) delay(175L * (attempt + 1))
        }
        throw IllegalStateException("native traffic monitor unavailable", lastError)
    }

    private fun disconnect() {
        if (!stopping.compareAndSet(false, true)) return
        tunnelJob?.cancel()
        tunnelJob = serviceScope.launch {
            trafficMonitor.stop()
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
        stopping.set(true)
        tunnelJob?.cancel()
        if (::trafficMonitor.isInitialized) trafficMonitor.stop()
        if (::core.isInitialized && core.isRunning()) {
            runCatching { runBlocking(Dispatchers.IO) { core.stop() } }
        }
        if (status.value.state == ServiceState.Connected || status.value.state == ServiceState.Connecting) {
            publish(ServiceState.Disconnected)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    private fun publish(state: ServiceState, failure: VpnFailure? = null) {
        status.value = VpnServiceStatus(
            state = state,
            failure = failure,
            traffic = if (::trafficMonitor.isInitialized) trafficMonitor.stats.value else VpnTrafficStats(),
        )
    }

    companion object {
        const val ACTION_CONNECT = "org.quickping.action.CONNECT"
        const val ACTION_DISCONNECT = "org.quickping.action.DISCONNECT"
        private const val CHANNEL_ID = "nimhub_vpn"
        private const val NOTIFICATION_ID = 2401
        val status = MutableStateFlow(VpnServiceStatus(ServiceState.Disconnected))
        private const val TAG = "nimHUBVpnService"
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
    val traffic: VpnTrafficStats = VpnTrafficStats(),
)

private fun Throwable.toVpnFailure(): VpnFailure {
    val source = generateSequence(this) { it.cause }.toList()
    val detail = source.mapNotNull { it.message }.firstOrNull { it.isNotBlank() }.orEmpty()
    val normalized = detail.lowercase()
    val (code, message) = when {
        "native traffic not observed" in normalized ->
            "traffic_unverified" to "تونل ساخته شد اما هستهٔ VPN هیچ عبور واقعی داده‌ای ثبت نکرد"
        "native traffic monitor unavailable" in normalized ->
            "traffic_monitor" to "بررسی آمار واقعی هستهٔ VPN راه‌اندازی نشد"
        "proxy verification failed" in normalized ->
            "proxy_unhealthy" to "پروکسی محلی راه‌اندازی شد اما عبور واقعی اینترنت از آن تأیید نشد"
        "tunnel verification failed" in normalized ->
            "tunnel_unhealthy" to "تونل VPN ساخته شد اما عبور واقعی اینترنت تأیید نشد"
        "tun interface" in normalized ->
            "tun_unavailable" to "رابط VPN اندروید ساخته نشد یا پیش از تأیید اتصال بسته شد"
        "native core stopped" in normalized ->
            "core_stopped" to "هستهٔ VPN پیش از تکمیل بررسی اتصال متوقف شد"
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
    return VpnFailure(
        code = code,
        userMessage = message,
        safeDetail = sanitizeVpnFailureDetail(detail, this::class.java.simpleName),
    )
}

internal fun sanitizeVpnFailureDetail(detail: String, fallback: String = "VPN error"): String {
    var sanitized = detail
        .replace(Regex("(?i)https?://\\S+"), "[url]")
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer [redacted]")
        .replace(
            Regex("(?i)(password|passwd|token|uuid|secret|authorization|private[_-]?key)[\\s\\\"']*[:=][\\s\\\"']*[^\\s,;\\\"'}]+"),
            "$1=[redacted]",
        )
        .replace(
            Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b"),
            "[uuid]",
        )
        .replace(
            Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"),
            "[email]",
        )
        .replace(
            Regex("(?<![A-Za-z0-9])[A-Za-z0-9_+/=-]{40,}(?![A-Za-z0-9])"),
            "[redacted]",
        )
        .trim()
        .take(240)
    if (sanitized.isBlank()) sanitized = fallback.take(80).ifBlank { "VPN error" }
    return sanitized
}
