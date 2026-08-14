package org.quickping.app.vpn

import android.os.SystemClock
import android.util.Log
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VpnTrafficStats(
    val uplinkBytesPerSecond: Long = 0L,
    val downlinkBytesPerSecond: Long = 0L,
    val uplinkTotalBytes: Long = 0L,
    val downlinkTotalBytes: Long = 0L,
    val connectionsIn: Int = 0,
    val connectionsOut: Int = 0,
    val available: Boolean = false,
    val sampleSequence: Long = 0L,
) {
    val totalBytes: Long
        get() = uplinkTotalBytes.coerceAtLeast(0L) + downlinkTotalBytes.coerceAtLeast(0L)
}

/**
 * Reads sing-box's own command-server status stream. This is deliberately
 * independent of Android TrafficStats: the release gate needs proof that bytes
 * were observed by the native proxy core, not merely that the app itself could
 * reach the internet through some other network.
 */
internal class VpnTrafficMonitor : CommandClientHandler {
    private val _stats = MutableStateFlow(VpnTrafficStats())
    val stats: StateFlow<VpnTrafficStats> = _stats.asStateFlow()
    private val sampleCounter = AtomicLong(0L)

    @Volatile
    private var client: CommandClient? = null

    @Synchronized
    fun start() {
        stop()
        sampleCounter.set(0L)
        _stats.value = VpnTrafficStats()
        val options = CommandClientOptions().apply {
            addCommand(Libbox.CommandStatus)
            statusInterval = STATUS_INTERVAL_NS
        }
        val candidate = CommandClient(this, options)
        candidate.connect()
        client = candidate
    }

    @Synchronized
    fun stop() {
        val current = client
        client = null
        if (current != null) {
            runCatching { current.disconnect() }
                .onFailure { Log.w(TAG, "Unable to disconnect traffic monitor", it) }
        }
        sampleCounter.set(0L)
        _stats.value = VpnTrafficStats()
    }

    suspend fun awaitInitialSample(timeoutMs: Long = DEFAULT_SAMPLE_TIMEOUT_MS): VpnTrafficStats? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            val current = _stats.value
            if (current.sampleSequence > 0L) return current
            delay(POLL_INTERVAL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return null
    }

    suspend fun awaitTrafficAfter(
        baseline: VpnTrafficStats,
        timeoutMs: Long = DEFAULT_TRAFFIC_TIMEOUT_MS,
    ): Boolean {
        require(baseline.sampleSequence > 0L) { "traffic baseline has no native sample" }
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            val current = _stats.value
            if (
                current.sampleSequence > baseline.sampleSequence &&
                current.uplinkTotalBytes > baseline.uplinkTotalBytes &&
                current.downlinkTotalBytes > baseline.downlinkTotalBytes
            ) {
                return true
            }
            delay(POLL_INTERVAL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }

    override fun connected() {
        Log.d(TAG, "Traffic command client connected")
    }

    override fun disconnected(message: String?) {
        Log.d(TAG, "Traffic command client disconnected: ${message.orEmpty()}")
    }

    override fun writeStatus(message: StatusMessage) {
        _stats.value = VpnTrafficStats(
            uplinkBytesPerSecond = message.uplink.coerceAtLeast(0L),
            downlinkBytesPerSecond = message.downlink.coerceAtLeast(0L),
            uplinkTotalBytes = message.uplinkTotal.coerceAtLeast(0L),
            downlinkTotalBytes = message.downlinkTotal.coerceAtLeast(0L),
            connectionsIn = message.connectionsIn.coerceAtLeast(0),
            connectionsOut = message.connectionsOut.coerceAtLeast(0),
            available = message.trafficAvailable,
            sampleSequence = sampleCounter.incrementAndGet(),
        )
    }

    override fun writeGroups(message: OutboundGroupIterator?) = Unit
    override fun setDefaultLogLevel(level: Int) = Unit
    override fun clearLogs() = Unit
    override fun writeLogs(messageList: LogIterator?) = Unit
    override fun initializeClashMode(modeList: StringIterator, currentMode: String) = Unit
    override fun updateClashMode(newMode: String) = Unit
    override fun writeConnectionEvents(events: ConnectionEvents?) = Unit

    private companion object {
        const val TAG = "nimHUBTraffic"
        const val STATUS_INTERVAL_NS = 500_000_000L
        const val DEFAULT_SAMPLE_TIMEOUT_MS = 4_000L
        const val DEFAULT_TRAFFIC_TIMEOUT_MS = 7_000L
        const val POLL_INTERVAL_MS = 150L
    }
}
