package org.quickping.app.model

enum class ConnectionStatus { Disconnected, Connecting, Connected, Error }

data class Server(
    val id: String,
    val countryCode: String,
    val countryName: String,
    val title: String,
    val remarks: String = "",
    val pingMs: Int? = null,
    val coreType: String = "sing-box",
    val freeAllowed: Boolean = false,
    val unmetered: Boolean = false,
)

data class Service(
    val id: String,
    val name: String,
    val plan: String,
    val license: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val daysLeft: Int,
    val usersCount: Int,
    val isFree: Boolean = false,
    val banned: Boolean = false,
) {
    val remainingBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes
}

data class UserInfo(
    val id: String,
    val email: String,
    val emailVerified: Boolean,
    val telegramBound: Boolean,
    val balance: Long,
    val language: String,
)

data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: String,
    val read: Boolean,
)

data class GuardianCategory(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val enabled: Boolean,
)

data class AppSettings(
    val darkTheme: Boolean = true,
    val autoConnect: Boolean = false,
    val autoPing: Boolean = true,
    val shareHotspot: Boolean = false,
    val proxyEnabled: Boolean = false,
    val splitTunnelingEnabled: Boolean = false,
    val guardianEnabled: Boolean = true,
    val dnsProvider: String = "پیش‌فرض",
    val proxyPort: Int = 10810,
    val language: String = "فارسی",
)
