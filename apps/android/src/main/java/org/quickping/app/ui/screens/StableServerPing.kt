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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.model.Server
import org.quickping.app.model.ServerPingState
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val STABLE_PING_CACHE_TTL_MS = 60_000L
private const val STABLE_PING_CONNECT_TIMEOUT_MS = 1_500
private const val STABLE_PING_PARALLELISM = 4
private const val STABLE_PING_PROBE_COUNT = 3

internal data class StablePingSnapshot(
    val state: ServerPingState,
    val valueMs: Int? = null,
    val measuredAtElapsedMs: Long = 0L,
)

private data class PhysicalNetworkCandidate(
    val network: Network,
    val validated: Boolean,
)

private data class TcpProbeBatch(
    val samples: List<Int>,
    val attempted: Boolean,
)

private val stableServerPingCache = ConcurrentHashMap<String, StablePingSnapshot>()

internal fun stablePingEndpointKey(server: Server): String =
    "${server.id}|${server.host.lowercase()}|${server.port}"

internal fun medianSuccessfulPing(samples: List<Int>): Int? {
    if (samples.isEmpty()) return null
    val sorted = samples.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

/** Compatibility helper retained for the existing bootstrap/cache regression tests. */
internal fun mergeStablePingValues(
    servers: List<Server>,
    cachedValues: Map<String, Int>,
): List<Server> = servers.map { server ->
    when {
        !server.selectable -> server.copy(pingMs = null, pingState = ServerPingState.UNKNOWN)
        server.pingMs != null -> server.copy(pingState = ServerPingState.AVAILABLE)
        else -> cachedValues[stablePingEndpointKey(server)]?.let {
            server.copy(pingMs = it, pingState = ServerPingState.AVAILABLE)
        } ?: server.copy(pingState = ServerPingState.CHECKING)
    }
}

internal fun applyStablePingSnapshots(
    servers: List<Server>,
    snapshots: Map<String, StablePingSnapshot>,
    selectedServerId: String,
    connectionStatus: ConnectionStatus,
): List<Server> = servers.map { server ->
    if (!server.selectable) {
        return@map server.copy(pingMs = null, pingState = ServerPingState.UNKNOWN)
    }

    val snapshot = snapshots[stablePingEndpointKey(server)]
    val isConnectedServer = connectionStatus == ConnectionStatus.Connected && server.id == selectedServerId
    if (isConnectedServer) {
        return@map server.copy(
            pingMs = snapshot?.valueMs,
            pingState = ServerPingState.CONNECTED,
        )
    }

    when (snapshot?.state) {
        ServerPingState.AVAILABLE -> server.copy(
            pingMs = snapshot.valueMs,
            pingState = ServerPingState.AVAILABLE,
        )
        ServerPingState.TIMEOUT -> server.copy(pingMs = null, pingState = ServerPingState.TIMEOUT)
        ServerPingState.UNKNOWN -> server.copy(pingMs = null, pingState = ServerPingState.UNKNOWN)
        ServerPingState.CONNECTED -> server.copy(pingMs = snapshot.valueMs, pingState = ServerPingState.CONNECTED)
        ServerPingState.CHECKING, null -> server.copy(pingMs = null, pingState = ServerPingState.CHECKING)
    }
}

/**
 * Measures the actual server host/port and deliberately avoids the default route when another VPN
 * owns it. Successful probes use the underlying Wi-Fi/cellular/ethernet Network socket factory.
 * Three short probes protect healthy servers from a one-off packet/connect failure; only a fully
 * measured, validated physical route can produce TIMEOUT. If Android cannot expose a trustworthy
 * route the result is UNKNOWN rather than a fabricated latency or a false timeout.
 */
@Composable
internal fun rememberStableServerPings(
    servers: List<Server>,
    enabled: Boolean,
    selectedServerId: String,
    connectionStatus: ConnectionStatus,
): List<Server> {
    val context = LocalContext.current.applicationContext
    val endpointKey = remember(servers) {
        servers.joinToString(separator = ";") {
            "${stablePingEndpointKey(it)}:${it.selectable}"
        }
    }
    var snapshots by remember(endpointKey) {
        val now = SystemClock.elapsedRealtime()
        mutableStateOf(
            buildMap {
                servers.filter { it.selectable }.forEach { server ->
                    val key = stablePingEndpointKey(server)
                    val cached = stableServerPingCache[key] ?: return@forEach
                    if (now - cached.measuredAtElapsedMs < STABLE_PING_CACHE_TTL_MS) put(key, cached)
                }
            },
        )
    }

    LaunchedEffect(endpointKey, enabled) {
        if (!enabled) {
            snapshots = emptyMap()
            return@LaunchedEffect
        }

        while (true) {
            val targets = servers.filter {
                it.selectable && it.host.isNotBlank() && it.port in 1..65535
            }
            if (targets.isEmpty()) {
                snapshots = emptyMap()
                return@LaunchedEffect
            }

            val semaphore = Semaphore(STABLE_PING_PARALLELISM)
            val measured = coroutineScope {
                targets.map { server ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val snapshot = measureServerEndpoint(context, server.host, server.port)
                            stablePingEndpointKey(server) to snapshot.copy(
                                measuredAtElapsedMs = SystemClock.elapsedRealtime(),
                            )
                        }
                    }
                }.awaitAll().toMap()
            }

            measured.forEach { (key, snapshot) -> stableServerPingCache[key] = snapshot }
            snapshots = measured
            delay(STABLE_PING_CACHE_TTL_MS)
        }
    }

    if (!enabled) {
        return servers.map { server ->
            if (server.selectable) server.copy(pingMs = null, pingState = ServerPingState.UNKNOWN)
            else server.copy(pingMs = null, pingState = ServerPingState.UNKNOWN)
        }
    }

    return applyStablePingSnapshots(
        servers = servers,
        snapshots = snapshots,
        selectedServerId = selectedServerId,
        connectionStatus = connectionStatus,
    )
}

private suspend fun measureServerEndpoint(
    context: Context,
    host: String,
    port: Int,
): StablePingSnapshot = withContext(Dispatchers.IO) {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return@withContext StablePingSnapshot(ServerPingState.UNKNOWN)
    val physical = physicalNetworks(connectivity)
    var conclusiveFailure = false

    for (candidate in physical) {
        val batch = tcpProbeBatch(candidate.network, host, port)
        medianSuccessfulPing(batch.samples)?.let {
            return@withContext StablePingSnapshot(ServerPingState.AVAILABLE, it)
        }
        if (candidate.validated && batch.attempted) conclusiveFailure = true
    }

    if (conclusiveFailure) {
        return@withContext StablePingSnapshot(ServerPingState.TIMEOUT)
    }

    val active = connectivity.activeNetwork
        ?: return@withContext StablePingSnapshot(ServerPingState.UNKNOWN)
    val activeCaps = connectivity.getNetworkCapabilities(active)
        ?: return@withContext StablePingSnapshot(ServerPingState.UNKNOWN)

    // Never fall back through another VPN: that is what produced unrealistically low values such
    // as +8 ms for unrelated NimHUB endpoints.
    if (activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
        return@withContext StablePingSnapshot(ServerPingState.UNKNOWN)
    }
    if (!activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        return@withContext StablePingSnapshot(ServerPingState.UNKNOWN)
    }

    val batch = tcpProbeBatch(null, host, port)
    medianSuccessfulPing(batch.samples)?.let {
        return@withContext StablePingSnapshot(ServerPingState.AVAILABLE, it)
    }
    if (activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && batch.attempted) {
        StablePingSnapshot(ServerPingState.TIMEOUT)
    } else {
        StablePingSnapshot(ServerPingState.UNKNOWN)
    }
}

private fun physicalNetworks(connectivity: ConnectivityManager): List<PhysicalNetworkCandidate> =
    connectivity.allNetworks
        .mapNotNull { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            PhysicalNetworkCandidate(
                network = network,
                validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            ) to capabilities
        }
        .sortedWith(
            compareByDescending<Pair<PhysicalNetworkCandidate, NetworkCapabilities>> { it.first.validated }
                .thenByDescending {
                    it.second.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        it.second.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                }
                .thenByDescending { it.second.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) },
        )
        .map { it.first }

private fun tcpProbeBatch(network: Network?, host: String, port: Int): TcpProbeBatch {
    val addresses: List<InetAddress?> = runCatching {
        if (network != null) {
            network.getAllByName(host).take(2).map { it as InetAddress? }
        } else {
            listOf(null)
        }
    }.getOrElse { return TcpProbeBatch(emptyList(), attempted = false) }
    if (addresses.isEmpty()) return TcpProbeBatch(emptyList(), attempted = false)

    val samples = ArrayList<Int>(STABLE_PING_PROBE_COUNT)
    repeat(STABLE_PING_PROBE_COUNT) { index ->
        val address = addresses[index % addresses.size]
        tcpConnectPingOnce(network, host, address, port)?.let(samples::add)
    }
    return TcpProbeBatch(samples = samples, attempted = true)
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
