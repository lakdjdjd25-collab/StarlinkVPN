package org.quickping.app.model

enum class ConnectionStatus { Disconnected, Connecting, Connected, Error }

data class Server(
    val id: String,
    val countryCode: String,
    val countryName: String,
    val title: String,
    val remarks: String = "",
    val host: String = "",
    val port: Int = 0,
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

data class AppRelease(
    val versionName: String,
    val versionCode: Int,
    val minimumVersionCode: Int,
    val mandatory: Boolean,
    val changelog: String,
    val downloadUrl: String,
    val sha256: String,
)

data class GuardianCategory(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val enabled: Boolean,
)

enum class SplitTunnelMode {
    Include,
    Exclude,
}

enum class DnsProvider(
    val storageValue: String,
    val persianLabel: String,
) {
    Default("default", "پیش‌فرض"),
    Cloudflare("cloudflare", "کلودفلر"),
    Google("google", "گوگل"),
    ;

    companion object {
        fun fromStorage(value: String?): DnsProvider = entries.firstOrNull {
            it.storageValue == value || it.persianLabel == value
        } ?: Default
    }
}

enum class AppLanguage(
    val code: String,
    val label: String,
) {
    Persian("fa", "فارسی"),
    English("en", "English"),
    Dutch("nl", "Nederlands"),
    Arabic("ar", "العربية"),
    Turkish("tr", "Türkçe"),
    Russian("ru", "Русский"),
    Hindi("hi", "हिन्दी"),
    Chinese("zh", "汉语"),
    Urdu("ur", "اُردُو"),
    ;

    companion object {
        fun fromCode(value: String?): AppLanguage = entries.firstOrNull {
            it.code == value || it.label == value
        } ?: Persian
    }
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    val systemApp: Boolean,
)

data class AppSettings(
    val darkTheme: Boolean = true,
    val autoConnect: Boolean = false,
    val autoPing: Boolean = true,
    val shareHotspot: Boolean = false,
    val proxyModeEnabled: Boolean = false,
    val localProxyEnabled: Boolean = true,
    val splitTunnelingEnabled: Boolean = false,
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.Exclude,
    val splitTunnelPackages: Set<String> = emptySet(),
    val splitTunnelAddresses: List<String> = emptyList(),
    val rememberSplitTunnelSettings: Boolean = true,
    val blockIrDomains: Boolean = true,
    val guardianEnabled: Boolean = true,
    val dnsProvider: DnsProvider = DnsProvider.Default,
    val proxyPort: Int = 10810,
    val language: AppLanguage = AppLanguage.Persian,
    val reconnectOnNetworkChange: Boolean = true,
    val strictRoute: Boolean = true,
    val ipv6Enabled: Boolean = true,
    val mtu: Int = 1500,
)
