package org.quickping.app.data.settings

import android.content.Context
import org.json.JSONArray

internal fun updateRecentServerIds(
    existing: List<String>,
    connectedServerId: String,
    validServerIds: Set<String>,
    limit: Int = 3,
): List<String> {
    if (limit <= 0) return emptyList()
    val selected = connectedServerId.trim()
    return buildList {
        if (selected.isNotEmpty() && selected in validServerIds) add(selected)
        existing.forEach { id ->
            if (size >= limit) return@forEach
            if (id in validServerIds && id != selected && id !in this) add(id)
        }
    }.take(limit)
}

internal class RecentServerStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun load(userId: String, serviceId: String, validServerIds: Set<String>): List<String> {
        if (userId.isBlank() || serviceId.isBlank() || validServerIds.isEmpty()) return emptyList()
        val existing = read(key(userId, serviceId))
        val filtered = existing.filter { it in validServerIds }.distinct().take(MAX_RECENT)
        if (filtered != existing) write(key(userId, serviceId), filtered)
        return filtered
    }

    fun record(
        userId: String,
        serviceId: String,
        serverId: String,
        validServerIds: Set<String>,
    ): List<String> {
        if (userId.isBlank() || serviceId.isBlank()) return emptyList()
        val storageKey = key(userId, serviceId)
        val updated = updateRecentServerIds(
            existing = read(storageKey),
            connectedServerId = serverId,
            validServerIds = validServerIds,
            limit = MAX_RECENT,
        )
        write(storageKey, updated)
        return updated
    }

    private fun read(storageKey: String): List<String> {
        val raw = preferences.getString(storageKey, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.optString(index).trim() }
                .filter(String::isNotEmpty)
                .distinct()
        }.getOrDefault(emptyList())
    }

    private fun write(storageKey: String, ids: List<String>) {
        preferences.edit().putString(storageKey, JSONArray(ids).toString()).apply()
    }

    private fun key(userId: String, serviceId: String): String =
        "recent_${userId.hashCode()}_${serviceId.hashCode()}"

    private companion object {
        const val PREFERENCES = "nimhub_recent_servers"
        const val MAX_RECENT = 3
    }
}
