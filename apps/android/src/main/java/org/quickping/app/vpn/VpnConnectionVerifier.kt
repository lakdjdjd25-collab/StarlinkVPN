package org.quickping.app.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.core.content.getSystemService
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.quickping.app.BuildConfig
import org.quickping.app.data.settings.QuickPingSettingsStore

internal fun requiredConnectivityProbeNames(
    guardianEnabled: Boolean,
    enabledGuardianCategories: Set<String>,
): Set<String> = buildSet {
    add("web")
    add("telegram")
    add("youtube")
    add("instagram")
    if (guardianEnabled && "socials" in enabledGuardianCategories) {
        remove("telegram")
        remove("instagram")
    }
}

/**
 * Starting libbox only proves that the configuration parsed and listeners were
 * created. Before the UI reports Connected, prove that real HTTPS traffic can
 * leave through the mode the user actually selected:
 *  - TUN mode requires Android to expose a VPN transport, then probes normally.
 *  - Proxy mode probes through nimHUB's local HTTP proxy and does not require a
 *    VPN transport because no TUN interface exists in that mode.
 *
 * A single generic health URL is not enough for this app. The release gate must
 * prove the destination classes the user explicitly relies on: ordinary Web,
 * Telegram, YouTube and Instagram. A valid HTTP status (including redirect or
 * access-denied) is sufficient because it proves DNS + TCP + TLS + HTTP reached
 * that remote service through the requested transport.
 *
 * A destination explicitly blocked by the user's Guardian policy is not a VPN
 * health failure. In particular, the Social networks category deliberately
 * rejects Telegram and Instagram domains, so those probes are skipped only when
 * that Guardian category is actually active.
 */
internal class VpnConnectionVerifier(
    private val context: Context,
) {
    suspend fun awaitHealthy(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        proxyPort: Int? = null,
    ): Boolean {
        require(proxyPort == null || proxyPort in 1024..65535) { "invalid local proxy port" }
        val groups = requiredProbeGroups()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            val transportReady = proxyPort != null || hasVpnTransport()
            if (transportReady && probeRequiredGroups(groups, proxyPort)) return true
            delay(RETRY_DELAY_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }

    private fun hasVpnTransport(): Boolean {
        val connectivity = context.getSystemService<ConnectivityManager>() ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun probeRequiredGroups(
        groups: List<ProbeGroup>,
        proxyPort: Int?,
    ): Boolean = coroutineScope {
        groups.map { group ->
            async(Dispatchers.IO) {
                group.urls.any { url -> probeHttps(url, proxyPort) }
            }
        }.awaitAll().all { it }
    }

    private fun requiredProbeGroups(): List<ProbeGroup> {
        val settingsStore = QuickPingSettingsStore(context)
        val settings = settingsStore.load()
        val enabledGuardian = settingsStore.enabledGuardianCategoryIds()
        val requiredNames = requiredConnectivityProbeNames(
            guardianEnabled = settings.guardianEnabled,
            enabledGuardianCategories = enabledGuardian,
        )

        return listOf(
            ProbeGroup(
                name = "web",
                urls = listOf(
                    "${BuildConfig.API_BASE_URL.trimEnd('/')}/api/v1/health",
                    "https://cp.cloudflare.com/generate_204",
                    "https://www.gstatic.com/generate_204",
                ).distinct(),
            ),
            ProbeGroup("telegram", listOf("https://telegram.org/")),
            ProbeGroup("youtube", listOf("https://www.youtube.com/")),
            ProbeGroup("instagram", listOf("https://www.instagram.com/")),
        ).filter { it.name in requiredNames }
    }

    private suspend fun probeHttps(url: String, proxyPort: Int?): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = URL(url)
            val connection = if (proxyPort == null) {
                endpoint.openConnection()
            } else {
                endpoint.openConnection(
                    Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress(LOCAL_PROXY_HOST, proxyPort),
                    ),
                )
            } as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = PROBE_TIMEOUT_MS
                connection.readTimeout = PROBE_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("Connection", "close")
                connection.setRequestProperty("User-Agent", "nimHUB/${BuildConfig.VERSION_NAME}")

                connection.responseCode in 100..599
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    private data class ProbeGroup(
        val name: String,
        val urls: List<String>,
    )

    private companion object {
        const val LOCAL_PROXY_HOST = "127.0.0.1"
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val PROBE_TIMEOUT_MS = 4_500
        const val RETRY_DELAY_MS = 900L
    }
}
