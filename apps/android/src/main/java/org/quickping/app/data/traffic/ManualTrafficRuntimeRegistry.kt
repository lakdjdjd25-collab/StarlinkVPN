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
    private var trafficMonitorRequired = false

    fun replace(value: PendingManualTrafficSession?) {
        synchronized(access) {
            pending = value
            trafficMonitorRequired = false
        }
    }

    fun take(): PendingManualTrafficSession? = synchronized(access) {
        pending.also { value ->
            pending = null
            // Accounting is a connection requirement only when this Manual Server is configured
            // to count traffic. An unmetered/manual tunnel must not fail solely because the
            // optional sing-box status monitor is unavailable.
            trafficMonitorRequired = value?.countTraffic == true
        }
    }

    fun trafficMonitoringRequired(): Boolean = synchronized(access) { trafficMonitorRequired }

    fun clear() {
        synchronized(access) {
            pending = null
            trafficMonitorRequired = false
        }
    }
}
