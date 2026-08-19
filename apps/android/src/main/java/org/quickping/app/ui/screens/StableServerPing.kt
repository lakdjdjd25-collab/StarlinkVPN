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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.quickping.app.model.Server
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val STABLE_PING_CACHE_TTL_MS = 60_000L
private const val STABLE_PING_CONNECT_TIMEOUT_MS = 1_500
private const val STABLE_PING_PARALLELISM = 4
private const val STABLE_PING_PROBE_COUNT = 3

private data class CachedServerPing(
    val valueMs: Int,
    val measuredAtElapsedMs: Long,
)

private val stableServerPingCache = ConcurrentHashMap<String, CachedServerPing>()

internal fun stablePingEndpointKey(server: Server): String =
    "${server.id}|${server.host.lowercase()}|${server.port}"

internal fun medianSuccessfulPing(samples: List<Int>): Int? {
    if (samples.isEmpty()) return null
    val sorted = samples.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

internal fun mergeStablePingValues(
    servers: List<Server>,
    cachedValues: Map<String, Int>,
): List<Server> = servers.map { server ->
    when {
        !server.selectable -> server.copy(pingMs = null)
        server.pingMs != null -> server
        else -> cachedValues[stablePingEndpointKey(server)]?.let { server.copy(pingMs = it) } ?: server
    }
}

/**
 * Stable endpoint ping based on the actual server host/port.
 *
 * The probe prefers a physical non-VPN Network. It never falls back through an active VPN because
 * that can produce misleadingly low values for unrelated endpoints. OEM/network exceptions are
 * isolated and degrade to an unavailable ping instead of escaping the Home coroutine.
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
        servers.joinToString(separator = ";") { "${stablePingEndpointKey(it)}:${it.pingMs ?: -1}:${it.selectable}" }
    }
    var measuredValues by remember(endpointKey) {
        mutableStateOf(
            servers.filter { it.selectable }.mapNotNull { server ->
                stableServerPingCache[stablePingEndpointKey(server)]?.valueMs?.let { value ->
                    stablePingEndpointKey(server) to value
                }
            }.toMap(),
        )
    }

    LaunchedEffect(endpointKey, observedPingKey, enabled) {
        val now = SystemClock.elapsedRealtime()

        servers.filter { it.selectable }.forEach { server ->
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
            if (!server.selectable || server.host.isBlank() || server.port !in 1..65535) return@filter false
            val cached = stableServerPingCache[stablePingEndpointKey(server)]
            cached == null || now - cached.measuredAtElapsedMs >= STABLE_PING_CACHE_TTL_MS
        }
        if (targets.isEmpty()) return@LaunchedEffect

        val semaphore = Semaphore(STABLE_PING_PARALLELISM)
        val results = coroutineScope {
            targets.map { server ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val value = safePhysicalNetworkTcpPing(context, server.host, server.port)
                        server to value
                    }
                }
            }.awaitAll()
        }

        results.forEach { (server, value) ->
            val key = stablePingEndpointKey(server)
            if (value == null) {
                stableServerPingCache.remove(key)
                measuredValues = measuredValues - key
            } else {
                stableServerPingCache[key] = CachedServerPing(value, SystemClock.elapsedRealtime())
                measuredValues = measuredValues + (key to value)
            }
        }
    }

    val cachedNow = buildMap {
        servers.filter { it.selectable }.forEach { server ->
            val key = stablePingEndpointKey(server)
            val value = measuredValues[key] ?: stableServerPingCache[key]?.valueMs
            if (value != null) put(key, value)
        }
    }
    return mergeStablePingValues(servers, cachedNow)
}

private suspend fun safePhysicalNetworkTcpPing(
    context: Context,
    host: String,
    port: Int,
): Int? = try {
    physicalNetworkTcpPing(context, host, port)
} catch (error: CancellationException) {
    throw error
} catch (_: Throwable) {
    null
}

private suspend fun physicalNetworkTcpPing(
    context: Context,
    host: String,
    port: Int,
): Int? = withContext(Dispatchers.IO) {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return@withContext null

    val candidates = physicalNetworks(connectivity)
    candidates.forEach { network ->
        tcpConnectMedian(network, host, port)?.let { return@withContext it }
    }

    // Only use the default route when it is a real non-VPN Internet network.
    val active = runCatching { connectivity.activeNetwork }.getOrNull()
        ?: return@withContext null
    val capabilities = runCatching { connectivity.getNetworkCapabilities(active) }.getOrNull()
        ?: return@withContext null
    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@withContext null
    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@withContext null

    tcpConnectMedian(null, host, port)
}

private fun physicalNetworks(connectivity: ConnectivityManager): List<Network> = runCatching {
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
}.getOrDefault(emptyList())

private fun tcpConnectMedian(network: Network?, host: String, port: Int): Int? {
    val addresses: List<InetAddress?> = runCatching {
        if (network != null) {
            network.getAllByName(host).take(2).map { it as InetAddress? }
        } else {
            listOf(null)
        }
    }.getOrElse { return null }
    if (addresses.isEmpty()) return null

    val samples = ArrayList<Int>(STABLE_PING_PROBE_COUNT)
    repeat(STABLE_PING_PROBE_COUNT) { index ->
        val address = addresses[index % addresses.size]
        tcpConnectPingOnce(network, host, address, port)?.let(samples::add)
    }
    return medianSuccessfulPing(samples)
}

private fun tcpConnectPingOnce(
    network: Network?,
    host: String,
    address: InetAddress?,
    port: Int,
): Int? = runCatching {
    val socket = network?.socketFactory?.createSocket() ?: Socket()
    socket.use {
        val started = System.nanoTime()
        val endpoint = if (address != null) InetSocketAddress(address, port) else InetSocketAddress(host, port)
        it.connect(endpoint, STABLE_PING_CONNECT_TIMEOUT_MS)
        ((System.nanoTime() - started) / 1_000_000.0)
            .roundToInt()
            .coerceAtLeast(1)
    }
}.getOrNull()
