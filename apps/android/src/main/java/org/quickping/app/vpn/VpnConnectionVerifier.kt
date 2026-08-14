package org.quickping.app.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.core.content.getSystemService
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.quickping.app.BuildConfig

/**
 * Starting libbox only proves that the configuration parsed and that the TUN
 * service was created. Before the UI reports Connected, prove two things:
 *  1. Android exposes the app's default network as a VPN transport.
 *  2. At least one real HTTPS request can complete through that network.
 *
 * Do not depend on one health endpoint. A single blocked/CDN/DNS endpoint used
 * to tear down an otherwise working tunnel in previous builds.
 */
internal class VpnConnectionVerifier(
    private val context: Context,
) {
    suspend fun awaitHealthy(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            if (hasVpnTransport() && probeAnyEndpoint()) return true
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

    private suspend fun probeAnyEndpoint(): Boolean = coroutineScope {
        probeUrls().map { url -> async(Dispatchers.IO) { probeHttps(url) } }
            .awaitAll()
            .any { it }
    }

    private fun probeUrls(): List<String> = listOf(
        "${BuildConfig.API_BASE_URL.trimEnd('/')}/api/v1/health",
        "https://cp.cloudflare.com/generate_204",
        "https://www.gstatic.com/generate_204",
        "https://telegram.org/",
    ).distinct()

    private suspend fun probeHttps(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = PROBE_TIMEOUT_MS
                connection.readTimeout = PROBE_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("Connection", "close")
                connection.setRequestProperty("User-Agent", "nimHUB/${BuildConfig.VERSION_NAME}")

                // Any valid HTTP response proves DNS + TCP + TLS + HTTP reached a
                // remote host. Do not require 2xx: a 3xx/4xx can still prove that
                // traffic is genuinely leaving through the tunnel.
                connection.responseCode in 100..599
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val PROBE_TIMEOUT_MS = 4_500
        const val RETRY_DELAY_MS = 900L
    }
}
