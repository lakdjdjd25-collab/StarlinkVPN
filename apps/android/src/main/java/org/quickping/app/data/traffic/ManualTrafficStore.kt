package org.quickping.app.data.traffic

import android.content.Context

internal data class ManualTrafficState(
    val sessionId: String,
    val serviceId: String,
    val serverId: String,
    val confirmedRemainingBytes: Long,
    val confirmedAtTotalBytes: Long,
    val uploadedBytes: Long,
    val downloadedBytes: Long,
    val countTraffic: Boolean,
    val pendingFinal: Boolean,
) {
    val totalBytes: Long get() = saturatedAdd(uploadedBytes, downloadedBytes)
    val unreportedBytes: Long get() = (totalBytes - confirmedAtTotalBytes).coerceAtLeast(0L)
    val localRemainingBytes: Long get() = if (countTraffic) {
        (confirmedRemainingBytes - unreportedBytes).coerceAtLeast(0L)
    } else {
        confirmedRemainingBytes.coerceAtLeast(0L)
    }
}

internal class ManualTrafficStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): ManualTrafficState? {
        val sessionId = preferences.getString(KEY_SESSION_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val serviceId = preferences.getString(KEY_SERVICE_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val serverId = preferences.getString(KEY_SERVER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        return ManualTrafficState(
            sessionId = sessionId,
            serviceId = serviceId,
            serverId = serverId,
            confirmedRemainingBytes = preferences.getLong(KEY_CONFIRMED_REMAINING, 0L).coerceAtLeast(0L),
            confirmedAtTotalBytes = preferences.getLong(KEY_CONFIRMED_AT_TOTAL, 0L).coerceAtLeast(0L),
            uploadedBytes = preferences.getLong(KEY_UPLOADED, 0L).coerceAtLeast(0L),
            downloadedBytes = preferences.getLong(KEY_DOWNLOADED, 0L).coerceAtLeast(0L),
            countTraffic = preferences.getBoolean(KEY_COUNT_TRAFFIC, true),
            pendingFinal = preferences.getBoolean(KEY_PENDING_FINAL, false),
        )
    }

    @Synchronized
    fun begin(
        sessionId: String,
        serviceId: String,
        serverId: String,
        remainingBytes: Long,
        countTraffic: Boolean,
    ): ManualTrafficState {
        val state = ManualTrafficState(
            sessionId = sessionId,
            serviceId = serviceId,
            serverId = serverId,
            confirmedRemainingBytes = remainingBytes.coerceAtLeast(0L),
            confirmedAtTotalBytes = 0L,
            uploadedBytes = 0L,
            downloadedBytes = 0L,
            countTraffic = countTraffic,
            pendingFinal = false,
        )
        write(state)
        return state
    }

    @Synchronized
    fun updateCounters(uploadedBytes: Long, downloadedBytes: Long): ManualTrafficState? {
        val current = load() ?: return null
        if (uploadedBytes < current.uploadedBytes || downloadedBytes < current.downloadedBytes) return current
        val state = current.copy(
            uploadedBytes = uploadedBytes.coerceAtLeast(0L),
            downloadedBytes = downloadedBytes.coerceAtLeast(0L),
        )
        write(state)
        return state
    }

    @Synchronized
    fun confirm(remainingBytes: Long, totalBytes: Long): ManualTrafficState? {
        val current = load() ?: return null
        val state = current.copy(
            confirmedRemainingBytes = remainingBytes.coerceAtLeast(0L),
            confirmedAtTotalBytes = totalBytes.coerceAtLeast(current.confirmedAtTotalBytes),
            pendingFinal = false,
        )
        write(state)
        return state
    }

    @Synchronized
    fun markPendingFinal(): ManualTrafficState? {
        val current = load() ?: return null
        val state = current.copy(pendingFinal = true)
        write(state)
        return state
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().commit()
    }

    private fun write(state: ManualTrafficState) {
        preferences.edit()
            .putString(KEY_SESSION_ID, state.sessionId)
            .putString(KEY_SERVICE_ID, state.serviceId)
            .putString(KEY_SERVER_ID, state.serverId)
            .putLong(KEY_CONFIRMED_REMAINING, state.confirmedRemainingBytes)
            .putLong(KEY_CONFIRMED_AT_TOTAL, state.confirmedAtTotalBytes)
            .putLong(KEY_UPLOADED, state.uploadedBytes)
            .putLong(KEY_DOWNLOADED, state.downloadedBytes)
            .putBoolean(KEY_COUNT_TRAFFIC, state.countTraffic)
            .putBoolean(KEY_PENDING_FINAL, state.pendingFinal)
            .commit()
    }

    private companion object {
        const val PREFS = "nimhub_manual_traffic_v1"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_SERVICE_ID = "service_id"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_CONFIRMED_REMAINING = "confirmed_remaining"
        const val KEY_CONFIRMED_AT_TOTAL = "confirmed_at_total"
        const val KEY_UPLOADED = "uploaded"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_COUNT_TRAFFIC = "count_traffic"
        const val KEY_PENDING_FINAL = "pending_final"
    }
}

internal fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
