package org.quickping.app.vpn

import org.json.JSONArray
import org.json.JSONObject
import org.quickping.app.model.AppSettings
import org.quickping.app.model.DnsProvider
import org.quickping.app.model.SplitTunnelMode

internal data class TunnelLaunchOptions(
    val includePackages: List<String> = emptyList(),
    val excludePackages: List<String> = emptyList(),
)

internal data class CompiledVpnConfig(
    val configJson: String,
    val launchOptions: TunnelLaunchOptions,
)

internal object VpnConfigCompiler {
    fun compile(
        rawConfigJson: String,
        settings: AppSettings,
        enabledGuardianCategories: Set<String>,
        applicationPackage: String,
    ): CompiledVpnConfig {
        val config = JSONObject(rawConfigJson)
        val route = config.optJSONObject("route") ?: JSONObject().also { config.put("route", it) }
        migrateLegacyProviderConfig(config, route)
        val proxyOutbound = route.optString("final").takeIf(String::isNotBlank)
            ?: firstUsableOutboundTag(config)
            ?: error("No usable proxy outbound is configured")

        config.remove("experimental")
        config.remove("ntp")
        compileInbounds(config, settings)
        compileDns(config, settings.dnsProvider)
        compileRoute(
            config,
            route,
            proxyOutbound,
            settings,
            if (settings.guardianEnabled) enabledGuardianCategories else emptySet(),
        )

        return CompiledVpnConfig(
            configJson = config.toString(),
            launchOptions = compilePackageOverrides(settings, applicationPackage),
        )
    }

    private fun compileInbounds(config: JSONObject, settings: AppSettings) {
        val inbounds = JSONArray()

        if (!settings.proxyModeEnabled) {
            val addresses = JSONArray().put("172.19.0.1/30")
            if (settings.ipv6Enabled) addresses.put("fdfe:dcba:9876::1/126")
            inbounds.put(
                0,
                JSONObject()
                    .put("type", "tun")
                    .put("tag", "tun-in")
                    .put("interface_name", "sing-tun")
                    .put("address", addresses)
                    .put("mtu", settings.mtu.coerceIn(1280, 9000))
                    .put("auto_route", true)
                    .put("strict_route", settings.strictRoute)
                    .put("stack", "mixed"),
            )
        }

        if (settings.localProxyEnabled || settings.proxyModeEnabled) {
            inbounds.put(
                JSONObject()
                    .put("type", "mixed")
                    .put("tag", LOCAL_PROXY_TAG)
                    .put("listen", if (settings.shareHotspot) "0.0.0.0" else "127.0.0.1")
                    .put("listen_port", settings.proxyPort.coerceIn(1024, 65535)),
            )
        }
        check(inbounds.length() > 0) { "At least one inbound is required" }
        config.put("inbounds", inbounds)
    }

    private fun compileDns(config: JSONObject, provider: DnsProvider) {
        if (provider == DnsProvider.Default) {
            config.put(
                "dns",
                JSONObject()
                    .put(
                        "servers",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "local")
                                .put("tag", CUSTOM_DNS_TAG),
                        ),
                    )
                    .put("final", CUSTOM_DNS_TAG)
                    .put("independent_cache", true),
            )
            return
        }
        val endpoint = when (provider) {
            DnsProvider.Cloudflare -> DnsEndpoint("1.1.1.1", "cloudflare-dns.com")
            DnsProvider.Google -> DnsEndpoint("8.8.8.8", "dns.google")
            DnsProvider.Default -> return
        }
        config.put(
            "dns",
            JSONObject()
                .put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "https")
                            .put("tag", CUSTOM_DNS_TAG)
                            .put("server", endpoint.address)
                            .put("server_port", 443)
                            .put("path", "/dns-query")
                            .put(
                                "tls",
                                JSONObject()
                                    .put("enabled", true)
                                    .put("server_name", endpoint.serverName),
                            ),
                    ),
                )
                .put("final", CUSTOM_DNS_TAG)
                .put("independent_cache", true),
        )
    }

    private fun compileRoute(
        config: JSONObject,
        route: JSONObject,
        proxyOutbound: String,
        settings: AppSettings,
        enabledGuardianCategories: Set<String>,
    ) {
        val providerRules = route.optJSONArray("rules")
        val rules = JSONArray()
        rules.put(JSONObject().put("action", "sniff"))
        rules.put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))

        if (settings.blockIrDomains) {
            rules.put(
                JSONObject()
                    .put("domain_suffix", JSONArray().put("ir"))
                    .put("action", "reject")
                    .put("method", "default"),
            )
        }
        guardianRule(enabledGuardianCategories)?.let { rules.put(it) }
        splitAddressRules(config, settings, proxyOutbound).forEach { rules.put(it) }
        if (providerRules != null) {
            for (index in 0 until providerRules.length()) {
                val providerRule = providerRules.optJSONObject(index) ?: continue
                val duplicateSniff = providerRule.optString("action") == "sniff"
                val duplicateDnsHijack = providerRule.optString("action") == "hijack-dns" ||
                    providerRule.optString("protocol") == "dns" &&
                    providerRule.optString("action").isBlank()
                if (!duplicateSniff && !duplicateDnsHijack) rules.put(providerRule)
            }
        }

        route.put("rules", rules)
        // Android must always protect the proxy's own sockets from the VPN.  This
        // is separate from the user-facing reconnect preference.
        route.put("auto_detect_interface", true)
        route.put("override_android_vpn", true)
        route.put("final", if (settings.splitTunnelingEnabled &&
            settings.splitTunnelMode == SplitTunnelMode.Include &&
            settings.splitTunnelAddresses.isNotEmpty()
        ) ensureDirectOutbound(config) else proxyOutbound)
    }

    private fun splitAddressRules(
        config: JSONObject,
        settings: AppSettings,
        proxyOutbound: String,
    ): List<JSONObject> {
        if (!settings.splitTunnelingEnabled || settings.splitTunnelAddresses.isEmpty()) return emptyList()
        val ipCidrs = mutableListOf<String>()
        val domainSuffixes = mutableListOf<String>()
        settings.splitTunnelAddresses.forEach { raw ->
            val value = raw.trim().lowercase().removePrefix("*.").removeSuffix(".")
            when {
                value.isBlank() -> Unit
                value.matches(Regex("[0-9a-f:.]+(?:/\\d{1,3})?")) -> ipCidrs += value
                value.matches(Regex("[a-z0-9-]+(?:\\.[a-z0-9-]+)+")) -> domainSuffixes += value
            }
        }
        if (ipCidrs.isEmpty() && domainSuffixes.isEmpty()) return emptyList()
        val outbound = if (settings.splitTunnelMode == SplitTunnelMode.Exclude) {
            ensureDirectOutbound(config)
        } else {
            proxyOutbound
        }
        return buildList {
            if (ipCidrs.isNotEmpty()) {
                add(
                    JSONObject()
                        .put("ip_cidr", JSONArray(ipCidrs.distinct()))
                        .put("action", "route")
                        .put("outbound", outbound),
                )
            }
            if (domainSuffixes.isNotEmpty()) {
                add(
                    JSONObject()
                        .put("domain_suffix", JSONArray(domainSuffixes.distinct()))
                        .put("action", "route")
                        .put("outbound", outbound),
                )
            }
        }
    }

    private fun guardianRule(enabledCategories: Set<String>): JSONObject? {
        val domains = enabledCategories.flatMap { category -> GUARDIAN_DOMAINS[category].orEmpty() }.distinct()
        if (domains.isEmpty()) return null
        return JSONObject()
            .put("domain_suffix", JSONArray(domains))
            .put("action", "reject")
            .put("method", "default")
    }

    private fun compilePackageOverrides(settings: AppSettings, applicationPackage: String): TunnelLaunchOptions {
        if (!settings.splitTunnelingEnabled) return TunnelLaunchOptions()
        val packages = settings.splitTunnelPackages
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
            .toList()
        return if (settings.splitTunnelMode == SplitTunnelMode.Include) {
            TunnelLaunchOptions(includePackages = (packages + applicationPackage).distinct())
        } else {
            TunnelLaunchOptions(excludePackages = packages.filterNot { it == applicationPackage })
        }
    }

    /**
     * PasarGuard and other subscription panels can still emit fields that were
     * accepted by sing-box 1.12 but were removed in 1.13.  The Android runtime
     * is pinned to 1.13, so migrate the safe/common legacy constructs before
     * Libbox.checkConfig validates the selected node.
     */
    private fun migrateLegacyProviderConfig(config: JSONObject, route: JSONObject) {
        val sourceOutbounds = config.optJSONArray("outbounds") ?: return
        val migratedOutbounds = JSONArray()
        val removedActions = mutableMapOf<String, String>()

        for (index in 0 until sourceOutbounds.length()) {
            val outbound = sourceOutbounds.optJSONObject(index) ?: continue
            val type = outbound.optString("type")
            val tag = outbound.optString("tag")
            when (type) {
                "block" -> if (tag.isNotBlank()) removedActions[tag] = "reject"
                "dns" -> if (tag.isNotBlank()) removedActions[tag] = "hijack-dns"
                else -> {
                    if (type == "direct") {
                        // Destination overrides moved to route actions in 1.11
                        // and the outbound fields were removed in 1.13.
                        outbound.remove("override_address")
                        outbound.remove("override_port")
                    }
                    migratedOutbounds.put(outbound)
                }
            }
        }

        if (removedActions.isNotEmpty()) {
            for (index in 0 until migratedOutbounds.length()) {
                val outbound = migratedOutbounds.optJSONObject(index) ?: continue
                if (outbound.optString("type") !in setOf("selector", "urltest")) continue
                val choices = outbound.optJSONArray("outbounds") ?: continue
                val migratedChoices = JSONArray()
                for (choiceIndex in 0 until choices.length()) {
                    val choice = choices.optString(choiceIndex)
                    if (choice.isNotBlank() && choice !in removedActions) migratedChoices.put(choice)
                }
                outbound.put("outbounds", migratedChoices)
            }
        }

        config.put("outbounds", migratedOutbounds)
        route.remove("geoip")
        route.remove("geosite")
        route.optJSONArray("rules")?.let { providerRules ->
            route.put("rules", migrateLegacyRouteRules(providerRules, removedActions))
        }
    }

    private fun migrateLegacyRouteRules(
        source: JSONArray,
        removedActions: Map<String, String>,
    ): JSONArray {
        val migrated = JSONArray()
        for (index in 0 until source.length()) {
            val rule = source.optJSONObject(index) ?: continue

            // GeoIP/Geosite rule items were removed in sing-box 1.12.  They
            // cannot be translated without their original databases, so omit
            // only those provider rules and retain the rest of the route.
            if (rule.has("geoip") || rule.has("source_geoip") || rule.has("geosite")) continue

            if (rule.has("rule_set_ipcidr_match_source")) {
                rule.put("rule_set_ip_cidr_match_source", rule.remove("rule_set_ipcidr_match_source"))
            }
            rule.optJSONArray("rules")?.let { nested ->
                rule.put("rules", migrateLegacyRouteRules(nested, removedActions))
            }

            when (removedActions[rule.optString("outbound")]) {
                "reject" -> {
                    rule.remove("outbound")
                    rule.put("action", "reject")
                    rule.put("method", "default")
                }
                "hijack-dns" -> {
                    rule.remove("outbound")
                    rule.put("action", "hijack-dns")
                }
            }
            migrated.put(rule)
        }
        return migrated
    }

    private fun ensureDirectOutbound(config: JSONObject): String {
        val outbounds = config.optJSONArray("outbounds") ?: JSONArray().also { config.put("outbounds", it) }
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            if (outbound.optString("type") == "direct") {
                val tag = outbound.optString("tag")
                if (tag.isNotBlank()) return tag
            }
        }
        outbounds.put(JSONObject().put("type", "direct").put("tag", DIRECT_OUTBOUND_TAG))
        return DIRECT_OUTBOUND_TAG
    }

    private fun firstUsableOutboundTag(config: JSONObject): String? {
        val outbounds = config.optJSONArray("outbounds") ?: return null
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            if (outbound.optString("type") !in setOf("direct", "block", "dns")) {
                return outbound.optString("tag").takeIf(String::isNotBlank)
            }
        }
        return null
    }

    private data class DnsEndpoint(val address: String, val serverName: String)

    private const val LOCAL_PROXY_TAG = "quickping-local-proxy"
    private const val CUSTOM_DNS_TAG = "quickping-dns"
    private const val DIRECT_OUTBOUND_TAG = "quickping-direct"

    private val GUARDIAN_DOMAINS = mapOf(
        "malware" to listOf("malware.testcategory.com", "malware.wicar.org"),
        "ads" to listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "app-measurement.com",
        ),
        "youtube" to listOf("googleads.g.doubleclick.net", "pagead2.googlesyndication.com"),
        "phishing" to listOf("phishing.testcategory.com"),
        "porn" to listOf("adult.testcategory.com"),
        "government" to listOf("gov.ir"),
        "payment" to listOf("shaparak.ir"),
        "socials" to listOf("facebook.com", "instagram.com", "tiktok.com", "x.com"),
        "crypto" to listOf("binance.com", "coinbase.com"),
        "fake-news" to emptyList(),
    )
}
