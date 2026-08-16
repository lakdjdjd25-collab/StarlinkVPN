package org.quickping.app.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.quickping.app.model.Server
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val STABLE_PING_CACHE_TTL_MS = 60_000L
private const val STABLE_PING_CONNECT_TIMEOUT_MS = 1_800
private const val STABLE_PING_PARALLELISM = 4

private data class CachedServerPing(
    val valueMs: Int,
    val measuredAtElapsedMs: Long,
)

private val stableServerPingCache = ConcurrentHashMap<String, CachedServerPing>()

internal fun stablePingEndpointKey(server: Server): String =
    "${server.id}|${server.host.lowercase()}|${server.port}"

internal fun mergeStablePingValues(
    servers: List<Server>,
    cachedValues: Map<String, Int>,
): List<Server> = servers.map { server ->
    if (server.pingMs != null) server
    else cachedValues[stablePingEndpointKey(server)]?.let { server.copy(pingMs = it) } ?: server
}

/**
 * Keeps the last successful latency visible while a background refresh runs. This prevents
 * navigation/account bootstrap refreshes from flashing every server back to "Check".
 *
 * Probes prefer a validated non-VPN network (Wi-Fi/cellular/ethernet) so latency remains available
 * whether NimHUB, another VPN, or no VPN is currently the system default route. If Android does not
 * expose an underlying physical network, the normal default route is used as a safe fallback.
 */
@Composable
internal fun rememberStableServerPings(
    servers: List<Server>,
    enabled: Boolean,
): List<Server> {
    val context = LocalContext.current.applicationContext
    val endpointKey = remember(servers) {
        servers.joinToString(separator = ";") { stablePingEndpointKey(it) }
    }
    val observedPingKey = remember(servers) {
        servers.joinToString(separator = ";") { "${stablePingEndpointKey(it)}:${it.pingMs ?: -1}" }
    }
    var measuredValues by remember(endpointKey) {
        mutableStateOf(
            servers.mapNotNull { server ->
                stableServerPingCache[stablePingEndpointKey(server)]?.valueMs?.let { value ->
                    stablePingEndpointKey(server) to value
                }
            }.toMap(),
        )
    }

    LaunchedEffect(endpointKey, observedPingKey, enabled) {
        val now = SystemClock.elapsedRealtime()

        // Accept any successful value already produced by the ViewModel and remember it across
        // bootstrap/navigation refreshes that recreate Server objects with pingMs = null.
        servers.forEach { server ->
            val ping = server.pingMs ?: return@forEach
            val key = stablePingEndpointKey(server)
            val previous = stableServerPingCache[key]
            if (previous == null || previous.valueMs != ping) {
                stableServerPingCache[key] = CachedServerPing(ping, now)
                measuredValues = measuredValues + (key to ping)
            }
        }

        if (!enabled) return@LaunchedEffect

        val targets = servers.filter { server ->
            if (server.host.isBlank() || server.port !in 1..65535) return@filter false
            val cached = stableServerPingCache[stablePingEndpointKey(server)]
            cached == null || now - cached.measuredAtElapsedMs >= STABLE_PING_CACHE_TTL_MS
        }
        if (targets.isEmpty()) return@LaunchedEffect

        val semaphore = Semaphore(STABLE_PING_PARALLELISM)
        coroutineScope {
            targets.map { server ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val value = physicalNetworkTcpPing(context, server.host, server.port)
                        server to value
                    }
                }
            }.awaitAll().forEach { (server, value) ->
                if (value == null) return@forEach
                val key = stablePingEndpointKey(server)
                stableServerPingCache[key] = CachedServerPing(value, SystemClock.elapsedRealtime())
                measuredValues = measuredValues + (key to value)
            }
        }
    }

    val cachedNow = buildMap {
        servers.forEach { server ->
            val key = stablePingEndpointKey(server)
            val value = measuredValues[key] ?: stableServerPingCache[key]?.valueMs
            if (value != null) put(key, value)
        }
    }
    return mergeStablePingValues(servers, cachedNow)
}

private suspend fun physicalNetworkTcpPing(
    context: Context,
    host: String,
    port: Int,
): Int? = withContext(Dispatchers.IO) {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val candidates = connectivity?.let(::physicalNetworks).orEmpty()

    candidates.forEach { network ->
        tcpConnectPing(network, host, port)?.let { return@withContext it }
    }

    // Fallback keeps compatibility on devices/OEMs that hide the underlying network while a VPN
    // owns the default route.
    tcpConnectPing(null, host, port)
}

private fun physicalNetworks(connectivity: ConnectivityManager): List<Network> =
    connectivity.allNetworks
        .mapNotNull { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            network to capabilities
        }
        .sortedWith(
            compareByDescending<Pair<Network, NetworkCapabilities>> {
                it.second.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }.thenByDescending {
                it.second.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    it.second.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            }.thenByDescending {
                it.second.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            },
        )
        .map { it.first }

private fun tcpConnectPing(network: Network?, host: String, port: Int): Int? = runCatching {
    val addresses = if (network != null) network.getAllByName(host).toList() else emptyList()
    val candidates = if (addresses.isNotEmpty()) addresses.take(2) else listOf(null)

    var best: Int? = null
    candidates.forEach { address ->
        val socket = network?.socketFactory?.createSocket() ?: Socket()
        socket.use {
            val started = System.nanoTime()
            val endpoint = if (address != null) InetSocketAddress(address, port) else InetSocketAddress(host, port)
            it.connect(endpoint, STABLE_PING_CONNECT_TIMEOUT_MS)
            val elapsed = ((System.nanoTime() - started) / 1_000_000.0)
                .roundToInt()
                .coerceAtLeast(1)
            best = best?.let { previous -> minOf(previous, elapsed) } ?: elapsed
        }
    }
    best
}.getOrNull()
