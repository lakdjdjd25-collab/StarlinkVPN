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

    fun replace(value: PendingManualTrafficSession?) {
        synchronized(access) { pending = value }
    }

    fun take(): PendingManualTrafficSession? = synchronized(access) {
        pending.also { pending = null }
    }

    fun clear() {
        synchronized(access) { pending = null }
    }
}
