package org.quickping.app.data.traffic

internal data class PendingManualTrafficSession(
    val sessionId: String,
    val serviceId: String,
    val serverId: String,
    val remainingBytes: Long,
    val countTraffic: Boolean,
)

internal object ManualTrafficRuntimeRegistry {
    private val access = Any()
    private var pending: PendingManualTrafficSession? = null
    private var activeManualTunnel = false

    fun replace(value: PendingManualTrafficSession?) {
        synchronized(access) {
            pending = value
            activeManualTunnel = false
        }
    }

    fun take(): PendingManualTrafficSession? = synchronized(access) {
        pending.also { value ->
            pending = null
            activeManualTunnel = value != null
        }
    }

    fun trafficMonitoringRequired(): Boolean = synchronized(access) { activeManualTunnel }

    fun clear() {
        synchronized(access) {
            pending = null
            activeManualTunnel = false
        }
    }
}
