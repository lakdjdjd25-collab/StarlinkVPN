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
        val proxyOutbound = configuredProxyOutboundTag(config, route)
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
            when (val address = parseSplitAddress(raw)) {
                null -> Unit
                else -> when (address.kind) {
                    SplitAddressKind.IpCidr -> ipCidrs += address.value
                    SplitAddressKind.DomainSuffix -> domainSuffixes += address.value
                }
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
        val sourceOutbounds = config.optJSONArray("outbounds") ?: JSONArray()
        val migratedOutbounds = JSONArray()
        val endpoints = config.optJSONArray("endpoints") ?: JSONArray()
        val endpointTags = (0 until endpoints.length())
            .mapNotNull { endpoints.optJSONObject(it)?.optString("tag")?.takeIf(String::isNotBlank) }
            .toMutableSet()
        val removedActions = mutableMapOf<String, String>()

        for (index in 0 until sourceOutbounds.length()) {
            val outbound = sourceOutbounds.optJSONObject(index) ?: continue
            val type = outbound.optString("type")
            val tag = outbound.optString("tag")
            when (type) {
                "block" -> if (tag.isNotBlank()) removedActions[tag] = "reject"
                "dns" -> if (tag.isNotBlank()) removedActions[tag] = "hijack-dns"
                "wireguard" -> {
                    // The legacy WireGuard outbound was removed in sing-box
                    // 1.13.  Endpoints can still be used as route targets, so
                    // preserve the tag while translating the old shape.
                    if (tag !in endpointTags) {
                        endpoints.put(migrateLegacyWireGuardOutbound(outbound))
                        endpointTags += tag
                    }
                }
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
        if (endpoints.length() > 0) config.put("endpoints", endpoints)
        route.remove("geoip")
        route.remove("geosite")
        route.optJSONArray("rules")?.let { providerRules ->
            route.put("rules", migrateLegacyRouteRules(providerRules, removedActions))
        }
    }

    private fun migrateLegacyWireGuardOutbound(outbound: JSONObject): JSONObject {
        val tag = outbound.optString("tag")
        require(tag.isNotBlank()) { "Legacy WireGuard config is missing an outbound tag" }

        val localAddresses = jsonStringArray(outbound, "local_address")
            .takeIf { it.length() > 0 }
            ?: jsonStringArray(outbound, "address")
        require(localAddresses.length() > 0) { "Legacy WireGuard config is missing a local address" }
        require(outbound.optString("private_key").isNotBlank()) {
            "Legacy WireGuard config is missing a private key"
        }

        val endpoint = JSONObject()
            .put("type", "wireguard")
            .put("tag", tag)
            .put("address", localAddresses)
            .put("private_key", outbound.get("private_key"))

        copyJsonField(outbound, endpoint, "system_interface", "system")
        copyJsonField(outbound, endpoint, "interface_name", "name")
        copyJsonField(outbound, endpoint, "mtu")
        copyJsonField(outbound, endpoint, "workers")
        WIREGUARD_DIAL_FIELDS.forEach { field -> copyJsonField(outbound, endpoint, field) }

        val legacyPeers = outbound.optJSONArray("peers")
        val peers = JSONArray()
        if (legacyPeers != null && legacyPeers.length() > 0) {
            for (index in 0 until legacyPeers.length()) {
                val peer = legacyPeers.optJSONObject(index) ?: continue
                peers.put(migrateLegacyWireGuardPeer(peer, localAddresses))
            }
        } else {
            peers.put(migrateLegacyWireGuardPeer(outbound, localAddresses))
        }
        require(peers.length() > 0) { "Legacy WireGuard config has no usable peer" }
        endpoint.put("peers", peers)
        return endpoint
    }

    private fun migrateLegacyWireGuardPeer(source: JSONObject, localAddresses: JSONArray): JSONObject {
        val address = source.optString("address").ifBlank { source.optString("server") }
        val port = when {
            source.has("port") -> source.optInt("port")
            else -> source.optInt("server_port")
        }
        val publicKey = source.optString("public_key").ifBlank { source.optString("peer_public_key") }
        require(address.isNotBlank() && port in 1..65535 && publicKey.isNotBlank()) {
            "Legacy WireGuard config contains an incomplete peer"
        }

        val peer = JSONObject()
            .put("address", address)
            .put("port", port)
            .put("public_key", publicKey)

        copyJsonField(source, peer, "pre_shared_key")
        copyJsonField(source, peer, "reserved")
        copyJsonField(source, peer, "persistent_keepalive_interval")
        val allowedIps = jsonStringArray(source, "allowed_ips")
        peer.put(
            "allowed_ips",
            if (allowedIps.length() > 0) allowedIps else defaultWireGuardAllowedIps(localAddresses),
        )
        return peer
    }

    private fun defaultWireGuardAllowedIps(localAddresses: JSONArray): JSONArray {
        var ipv4 = false
        var ipv6 = false
        for (index in 0 until localAddresses.length()) {
            val address = localAddresses.optString(index)
            if (':' in address) ipv6 = true else if (address.isNotBlank()) ipv4 = true
        }
        return JSONArray().apply {
            if (ipv4 || !ipv6) put("0.0.0.0/0")
            if (ipv6) put("::/0")
        }
    }

    private fun jsonStringArray(source: JSONObject, field: String): JSONArray {
        source.optJSONArray(field)?.let { return JSONArray(it.toString()) }
        return source.optString(field)
            .takeIf(String::isNotBlank)
            ?.let { JSONArray().put(it) }
            ?: JSONArray()
    }

    private fun copyJsonField(
        source: JSONObject,
        destination: JSONObject,
        sourceField: String,
        destinationField: String = sourceField,
    ) {
        if (source.has(sourceField) && !source.isNull(sourceField)) {
            destination.put(destinationField, source.get(sourceField))
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

    private fun configuredProxyOutboundTag(config: JSONObject, route: JSONObject): String? {
        val configured = route.optString("final").takeIf(String::isNotBlank) ?: return null
        config.optJSONArray("outbounds")?.let { outbounds ->
            for (index in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(index) ?: continue
                if (outbound.optString("tag") != configured) continue
                return configured.takeIf { outbound.optString("type") !in setOf("direct", "block", "dns") }
            }
        }
        config.optJSONArray("endpoints")?.let { endpoints ->
            for (index in 0 until endpoints.length()) {
                val endpoint = endpoints.optJSONObject(index) ?: continue
                if (endpoint.optString("tag") == configured) return configured
            }
        }
        return null
    }

    private fun firstUsableOutboundTag(config: JSONObject): String? {
        config.optJSONArray("outbounds")?.let { outbounds ->
            for (index in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(index) ?: continue
                if (outbound.optString("type") !in setOf("direct", "block", "dns")) {
                    return outbound.optString("tag").takeIf(String::isNotBlank)
                }
            }
        }
        val endpoints = config.optJSONArray("endpoints") ?: return null
        for (index in 0 until endpoints.length()) {
            val endpoint = endpoints.optJSONObject(index) ?: continue
            endpoint.optString("tag").takeIf(String::isNotBlank)?.let { return it }
        }
        return null
    }

    private data class DnsEndpoint(val address: String, val serverName: String)

    private const val LOCAL_PROXY_TAG = "quickping-local-proxy"
    private const val CUSTOM_DNS_TAG = "quickping-dns"
    private const val DIRECT_OUTBOUND_TAG = "quickping-direct"

    private val WIREGUARD_DIAL_FIELDS = setOf(
        "detour",
        "bind_interface",
        "inet4_bind_address",
        "inet6_bind_address",
        "bind_address_no_port",
        "routing_mark",
        "reuse_addr",
        "netns",
        "connect_timeout",
        "tcp_fast_open",
        "tcp_multi_path",
        "disable_tcp_keep_alive",
        "tcp_keep_alive",
        "tcp_keep_alive_interval",
        "udp_fragment",
        "domain_resolver",
        "network_strategy",
        "network_type",
        "fallback_network_type",
        "fallback_delay",
        "domain_strategy",
    )

    private val GUARDIAN_DOMAINS = mapOf(
        "malware" to listOf(
            "malware.testcategory.com",
            "malware.wicar.org",
            "malware.testing.google.test",
        ),
        "ads" to listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "app-measurement.com",
            "adnxs.com",
            "adsrvr.org",
            "criteo.com",
            "criteo.net",
            "scorecardresearch.com",
            "taboola.com",
            "outbrain.com",
        ),
        "youtube" to listOf(
            "googleads.g.doubleclick.net",
            "pagead2.googlesyndication.com",
            "youtubeads.googleapis.com",
        ),
        "phishing" to listOf(
            "phishing.testcategory.com",
            "testsafebrowsing.appspot.com",
        ),
        "porn" to listOf(
            "adult.testcategory.com",
            "pornhub.com",
            "xvideos.com",
            "xnxx.com",
            "redtube.com",
            "youporn.com",
        ),
        "government" to listOf("gov.ir"),
        "payment" to listOf(
            "shaparak.ir",
            "behpardakht.com",
            "pec.ir",
            "sadadpsp.ir",
            "asanpardakht.ir",
            "sepehrpay.com",
        ),
        "socials" to listOf(
            "facebook.com",
            "fbcdn.net",
            "instagram.com",
            "whatsapp.com",
            "tiktok.com",
            "x.com",
            "twitter.com",
            "telegram.org",
            "t.me",
            "snapchat.com",
        ),
        "crypto" to listOf(
            "binance.com",
            "coinbase.com",
            "kraken.com",
            "kucoin.com",
            "bybit.com",
            "okx.com",
            "crypto.com",
            "metamask.io",
        ),
        "fake-news" to listOf(
            "fake-news.testcategory.com",
            "worldnewsdailyreport.com",
            "empirenews.net",
            "nationalreport.net",
            "huzlers.com",
        ),
    )
}
