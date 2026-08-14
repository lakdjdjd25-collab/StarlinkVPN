package org.quickping.app.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A started libbox service is not enough to call the VPN "Connected".
 * Verify that Android has an active VPN transport and that normal HTTPS traffic
 * can actually leave the process after the TUN is established.
 */
internal class VpnConnectionVerifier(
    private val context: Context,
) {
    suspend fun awaitHealthy(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        do {
            if (hasVpnTransport() && probeControlPlane()) return true
            delay(RETRY_DELAY_MS)
        } while (System.currentTimeMillis() < deadline)
        return false
    }

    private fun hasVpnTransport(): Boolean {
        val connectivity = context.getSystemService<ConnectivityManager>() ?: return false
        return connectivity.allNetworks.any { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@any false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    private suspend fun probeControlPlane(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(HEALTH_URL).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = PROBE_TIMEOUT_MS
                connection.readTimeout = PROBE_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    private companion object {
        const val HEALTH_URL = "https://control-plane-production-a517.up.railway.app/api/v1/health"
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val PROBE_TIMEOUT_MS = 4_000
        const val RETRY_DELAY_MS = 750L
    }
}
