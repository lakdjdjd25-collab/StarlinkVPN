package org.quickping.app.data.settings

import android.content.Context
import org.json.JSONArray
import org.quickping.app.model.AppLanguage
import org.quickping.app.model.AppSettings
import org.quickping.app.model.DnsProvider
import org.quickping.app.model.GuardianCategory
import org.quickping.app.model.SplitTunnelMode

class QuickPingSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        migrateLegacyRuntimeSettings()
        return AppSettings(
            darkTheme = preferences.getBoolean(KEY_DARK_THEME, true),
            autoConnect = preferences.getBoolean(KEY_AUTO_CONNECT, false),
            autoPing = preferences.getBoolean(KEY_AUTO_PING, true),
            shareHotspot = preferences.getBoolean(KEY_SHARE_HOTSPOT, false),
            proxyModeEnabled = preferences.getBoolean(KEY_PROXY_MODE, false),
            localProxyEnabled = preferences.getBoolean(KEY_LOCAL_PROXY, true),
            splitTunnelingEnabled = preferences.getBoolean(KEY_SPLIT_ENABLED, false),
            splitTunnelMode = runCatching {
                SplitTunnelMode.valueOf(preferences.getString(KEY_SPLIT_MODE, null).orEmpty())
            }.getOrDefault(SplitTunnelMode.Exclude),
            splitTunnelPackages = preferences.getStringSet(KEY_SPLIT_PACKAGES, emptySet()).orEmpty().toSet(),
            splitTunnelAddresses = preferences.getString(KEY_SPLIT_ADDRESSES, null).toStringList(),
            rememberSplitTunnelSettings = preferences.getBoolean(KEY_SPLIT_REMEMBER, true),
            blockIrDomains = preferences.getBoolean(KEY_BLOCK_IR_DOMAINS, true),
            guardianEnabled = preferences.getBoolean(KEY_GUARDIAN_ENABLED, true),
            dnsProvider = DnsProvider.fromStorage(preferences.getString(KEY_DNS_PROVIDER, null)),
            proxyPort = preferences.getInt(KEY_PROXY_PORT, 10810).coerceIn(1024, 65535),
            language = AppLanguage.fromCode(preferences.getString(KEY_LANGUAGE, null)),
            reconnectOnNetworkChange = preferences.getBoolean(KEY_RECONNECT, true),
            strictRoute = preferences.getBoolean(KEY_STRICT_ROUTE, true),
            ipv6Enabled = preferences.getBoolean(KEY_IPV6, true),
            mtu = preferences.getInt(KEY_MTU, 1500).coerceIn(1280, 9000),
        )
    }

    fun save(settings: AppSettings) {
        preferences.edit()
            .putInt(KEY_SETTINGS_SCHEMA_VERSION, CURRENT_SETTINGS_SCHEMA_VERSION)
            .putBoolean(KEY_DARK_THEME, settings.darkTheme)
            .putBoolean(KEY_AUTO_CONNECT, settings.autoConnect)
            .putBoolean(KEY_AUTO_PING, settings.autoPing)
            .putBoolean(KEY_SHARE_HOTSPOT, settings.shareHotspot)
            .putBoolean(KEY_PROXY_MODE, settings.proxyModeEnabled)
            .putBoolean(KEY_LOCAL_PROXY, settings.localProxyEnabled)
            .putBoolean(KEY_SPLIT_ENABLED, settings.splitTunnelingEnabled)
            .putString(KEY_SPLIT_MODE, settings.splitTunnelMode.name)
            .putStringSet(KEY_SPLIT_PACKAGES, settings.splitTunnelPackages)
            .putString(KEY_SPLIT_ADDRESSES, JSONArray(settings.splitTunnelAddresses).toString())
            .putBoolean(KEY_SPLIT_REMEMBER, settings.rememberSplitTunnelSettings)
            .putBoolean(KEY_BLOCK_IR_DOMAINS, settings.blockIrDomains)
            .putBoolean(KEY_GUARDIAN_ENABLED, settings.guardianEnabled)
            .putString(KEY_DNS_PROVIDER, settings.dnsProvider.storageValue)
            .putInt(KEY_PROXY_PORT, settings.proxyPort.coerceIn(1024, 65535))
            .putString(KEY_LANGUAGE, settings.language.code)
            .putBoolean(KEY_RECONNECT, settings.reconnectOnNetworkChange)
            .putBoolean(KEY_STRICT_ROUTE, settings.strictRoute)
            .putBoolean(KEY_IPV6, settings.ipv6Enabled)
            .putInt(KEY_MTU, settings.mtu.coerceIn(1280, 9000))
            .apply()
    }

    fun reset(): AppSettings {
        preferences.edit().clear().putInt(KEY_SETTINGS_SCHEMA_VERSION, CURRENT_SETTINGS_SCHEMA_VERSION).apply()
        return AppSettings()
    }

    fun mergeGuardian(categories: List<GuardianCategory>): List<GuardianCategory> = categories.map { category ->
        val key = guardianKey(category.id)
        if (preferences.contains(key)) category.copy(enabled = preferences.getBoolean(key, category.enabled)) else category
    }

    fun saveGuardian(categories: List<GuardianCategory>) {
        preferences.edit().apply {
            categories.forEach { category -> putBoolean(guardianKey(category.id), category.enabled) }
        }.apply()
    }

    fun enabledGuardianCategoryIds(): Set<String> = GUARDIAN_CATEGORY_IDS.filterTo(linkedSetOf()) { id ->
        preferences.getBoolean(guardianKey(id), id in DEFAULT_GUARDIAN_CATEGORIES)
    }

    private fun migrateLegacyRuntimeSettings() {
        val currentVersion = preferences.getInt(KEY_SETTINGS_SCHEMA_VERSION, 0)
        if (currentVersion >= CURRENT_SETTINGS_SCHEMA_VERSION) return

        val splitMode = runCatching {
            SplitTunnelMode.valueOf(preferences.getString(KEY_SPLIT_MODE, null).orEmpty())
        }.getOrDefault(SplitTunnelMode.Exclude)
        val staleIncludeWhitelist = preferences.getBoolean(KEY_SPLIT_ENABLED, false) &&
            splitMode == SplitTunnelMode.Include

        // Earlier builds could persist an Include-only app list indefinitely.
        // After an upgrade that state can make the VPN appear connected while
        // only one previously selected app (commonly Telegram) enters the TUN.
        // Preserve the user's saved list, but require them to explicitly enable
        // split tunneling again after this migration. Normal VPN mode is full tunnel.
        preferences.edit().apply {
            if (staleIncludeWhitelist) putBoolean(KEY_SPLIT_ENABLED, false)
            putInt(KEY_SETTINGS_SCHEMA_VERSION, CURRENT_SETTINGS_SCHEMA_VERSION)
        }.apply()
    }

    private fun String?.toStringList(): List<String> {
        if (isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(this)
            List(array.length()) { index -> array.optString(index) }
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
        }.getOrDefault(emptyList())
    }

    private fun guardianKey(categoryId: String) = "guardian_category_$categoryId"

    companion object {
        const val PREFERENCES = "quickping"
        const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_SETTINGS_SCHEMA_VERSION = "settings_schema_version"
        private const val CURRENT_SETTINGS_SCHEMA_VERSION = 2
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_AUTO_PING = "auto_ping"
        private const val KEY_SHARE_HOTSPOT = "share_hotspot"
        private const val KEY_PROXY_MODE = "proxy_mode"
        private const val KEY_LOCAL_PROXY = "local_proxy"
        private const val KEY_SPLIT_ENABLED = "split_enabled"
        private const val KEY_SPLIT_MODE = "split_mode"
        private const val KEY_SPLIT_PACKAGES = "split_packages"
        private const val KEY_SPLIT_ADDRESSES = "split_addresses"
        private const val KEY_SPLIT_REMEMBER = "split_remember"
        private const val KEY_BLOCK_IR_DOMAINS = "block_ir_domains"
        private const val KEY_GUARDIAN_ENABLED = "guardian_enabled"
        private const val KEY_DNS_PROVIDER = "dns_provider"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_RECONNECT = "reconnect_on_network_change"
        private const val KEY_STRICT_ROUTE = "strict_route"
        private const val KEY_IPV6 = "ipv6_enabled"
        private const val KEY_MTU = "mtu"
        private val GUARDIAN_CATEGORY_IDS = setOf(
            "malware",
            "ads",
            "youtube",
            "phishing",
            "porn",
            "government",
            "payment",
            "socials",
            "crypto",
            "fake-news",
        )
        private val DEFAULT_GUARDIAN_CATEGORIES = setOf("malware", "ads", "youtube", "phishing")
    }
}
