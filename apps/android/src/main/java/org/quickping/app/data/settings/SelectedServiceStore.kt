package org.quickping.app.data.settings

import android.content.Context

internal fun resolveSelectedServiceId(
    preferredId: String?,
    availableIds: List<String>,
): String = preferredId
    ?.trim()
    ?.takeIf { it.isNotEmpty() && it in availableIds }
    ?: availableIds.firstOrNull().orEmpty()

internal class SelectedServiceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun load(userId: String): String? {
        if (userId.isBlank()) return null
        return preferences.getString(key(userId), null)?.trim()?.takeIf(String::isNotEmpty)
    }

    fun save(userId: String, serviceId: String) {
        if (userId.isBlank() || serviceId.isBlank()) return
        preferences.edit().putString(key(userId), serviceId).apply()
    }

    private fun key(userId: String): String = "selected:$userId"

    private companion object {
        const val PREFERENCES = "nimhub_selected_service"
    }
}
