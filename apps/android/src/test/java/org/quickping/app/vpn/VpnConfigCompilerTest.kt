package org.quickping.app.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.quickping.app.model.AppSettings
import org.quickping.app.model.DnsProvider
import org.quickping.app.model.SplitTunnelMode

class VpnConfigCompilerTest {
    @Test
    fun `canonical Android config removes provider listeners and applies per-app exclusion`() {
        val result = VpnConfigCompiler.compile(
            rawConfigJson = providerConfig,
            settings = AppSettings(
                splitTunnelingEnabled = true,
                splitTunnelMode = SplitTunnelMode.Exclude,
                splitTunnelPackages = setOf("com.example.direct"),
            ),
            enabledGuardianCategories = setOf("ads"),
            applicationPackage = "org.quickping",
        )

        val config = JSONObject(result.configJson)
        val inbounds = config.getJSONArray("inbounds")
        assertEquals("tun", inbounds.getJSONObject(0).getString("type"))
        assertTrue((0 until inbounds.length()).none { inbounds.getJSONObject(it).optInt("listen_port") == 9090 })
        assertEquals("local", config.getJSONObject("dns").getJSONArray("servers").getJSONObject(0).getString("type"))
        assertFalse(config.has("experimental"))
        assertEquals(listOf("com.example.direct"), result.launchOptions.excludePackages)
        assertTrue(result.launchOptions.includePackages.isEmpty())
        val rules = config.getJSONObject("route").getJSONArray("rules")
        assertTrue((0 until rules.length()).any { rules.getJSONObject(it).optString("action") == "reject" })
        assertTrue((0 until rules.length()).any { rules.getJSONObject(it).optString("rule_set") == "geo" })
        assertTrue(config.getJSONObject("route").has("rule_set"))
    }

    @Test
    fun `proxy mode starts only a private mixed listener and does not require TUN`() {
        val result = VpnConfigCompiler.compile(
            rawConfigJson = providerConfig,
            settings = AppSettings(
                proxyModeEnabled = true,
                localProxyEnabled = true,
                proxyPort = 10810,
                dnsProvider = DnsProvider.Cloudflare,
            ),
            enabledGuardianCategories = emptySet(),
            applicationPackage = "org.quickping",
        )

        val config = JSONObject(result.configJson)
        val inbounds = config.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        assertEquals("mixed", inbounds.getJSONObject(0).getString("type"))
        assertEquals("127.0.0.1", inbounds.getJSONObject(0).getString("listen"))
        assertEquals(10810, inbounds.getJSONObject(0).getInt("listen_port"))
        assertFalse(inbounds.getJSONObject(0).has("sniff"))
        assertEquals("https", config.getJSONObject("dns").getJSONArray("servers").getJSONObject(0).getString("type"))
    }

    @Test
    fun `legacy special outbounds are migrated for sing-box 1_13`() {
        val result = VpnConfigCompiler.compile(
            rawConfigJson = legacyProviderConfig,
            settings = AppSettings(blockIrDomains = false),
            enabledGuardianCategories = emptySet(),
            applicationPackage = "org.quickping",
        )

        val config = JSONObject(result.configJson)
        val outbounds = config.getJSONArray("outbounds")
        assertFalse((0 until outbounds.length()).any { index ->
            outbounds.getJSONObject(index).optString("type") in setOf("block", "dns")
        })
        assertFalse(outbounds.getJSONObject(1).has("override_address"))
        val rules = config.getJSONObject("route").getJSONArray("rules")
        assertTrue((0 until rules.length()).any { rules.getJSONObject(it).optString("action") == "reject" })
        assertTrue((0 until rules.length()).any { rules.getJSONObject(it).optString("action") == "hijack-dns" })
    }

    @Test
    fun `legacy WireGuard outbound is migrated to a sing-box 1_13 endpoint`() {
        val result = VpnConfigCompiler.compile(
            rawConfigJson = legacyWireGuardConfig,
            settings = AppSettings(blockIrDomains = false),
            enabledGuardianCategories = emptySet(),
            applicationPackage = "org.quickping",
        )

        val config = JSONObject(result.configJson)
        assertEquals(0, config.getJSONArray("outbounds").length())
        val endpoint = config.getJSONArray("endpoints").getJSONObject(0)
        assertEquals("wireguard", endpoint.getString("type"))
        assertEquals("wg-selected", endpoint.getString("tag"))
        assertEquals("10.20.0.2/32", endpoint.getJSONArray("address").getString(0))
        assertFalse(endpoint.has("server"))
        assertFalse(endpoint.has("server_port"))
        assertFalse(endpoint.has("local_address"))
        assertFalse(endpoint.has("peer_public_key"))
        val peer = endpoint.getJSONArray("peers").getJSONObject(0)
        assertEquals("wg.example.com", peer.getString("address"))
        assertEquals(51820, peer.getInt("port"))
        assertEquals("peer-public-key", peer.getString("public_key"))
        assertEquals("0.0.0.0/0", peer.getJSONArray("allowed_ips").getString(0))
        assertEquals("wg-selected", config.getJSONObject("route").getString("final"))
    }

    @Test
    fun `include split mode with no selected apps does not accidentally tunnel every app`() {
        val result = VpnConfigCompiler.compile(
            rawConfigJson = providerConfig,
            settings = AppSettings(
                splitTunnelingEnabled = true,
                splitTunnelMode = SplitTunnelMode.Include,
                splitTunnelPackages = emptySet(),
            ),
            enabledGuardianCategories = emptySet(),
            applicationPackage = "org.quickping",
        )

        assertEquals(listOf("org.quickping"), result.launchOptions.includePackages)
        assertTrue(result.launchOptions.excludePackages.isEmpty())
    }

    @Test
    fun `include address mode proxies selected domains and routes the rest directly`() {
        val result = VpnConfigCompiler.compile(
            rawConfigJson = providerConfig,
            settings = AppSettings(
                splitTunnelingEnabled = true,
                splitTunnelMode = SplitTunnelMode.Include,
                splitTunnelAddresses = listOf("example.com"),
                blockIrDomains = false,
            ),
            enabledGuardianCategories = emptySet(),
            applicationPackage = "org.quickping",
        )

        val config = JSONObject(result.configJson)
        val route = config.getJSONObject("route")
        assertEquals("direct", route.getString("final"))
        val domainRule = (0 until route.getJSONArray("rules").length())
            .map { route.getJSONArray("rules").getJSONObject(it) }
            .first { it.has("domain_suffix") }
        assertEquals("selected", domainRule.getString("outbound"))
    }

    @Test
    fun `ir blocking adds an explicit reject rule and can be disabled`() {
        val blocked = JSONObject(
            VpnConfigCompiler.compile(
                rawConfigJson = providerConfig,
                settings = AppSettings(blockIrDomains = true),
                enabledGuardianCategories = emptySet(),
                applicationPackage = "org.quickping",
            ).configJson,
        ).getJSONObject("route").getJSONArray("rules")
        assertTrue((0 until blocked.length()).any { index ->
            val rule = blocked.getJSONObject(index)
            rule.optJSONArray("domain_suffix")?.let { suffixes ->
                (0 until suffixes.length()).any { suffixes.getString(it) == "ir" }
            } == true && rule.optString("action") == "reject"
        })

        val allowed = JSONObject(
            VpnConfigCompiler.compile(
                rawConfigJson = providerConfig,
                settings = AppSettings(blockIrDomains = false),
                enabledGuardianCategories = emptySet(),
                applicationPackage = "org.quickping",
            ).configJson,
        ).getJSONObject("route").getJSONArray("rules")
        assertFalse((0 until allowed.length()).any { index ->
            allowed.getJSONObject(index).optJSONArray("domain_suffix")?.let { suffixes ->
                (0 until suffixes.length()).any { suffixes.getString(it) == "ir" }
            } == true
        })
    }

    @Test
    fun `every Guardian category produces an enforceable reject rule`() {
        val categories = setOf(
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
        categories.forEach { category ->
            val rules = JSONObject(
                VpnConfigCompiler.compile(
                    rawConfigJson = providerConfig,
                    settings = AppSettings(blockIrDomains = false, guardianEnabled = true),
                    enabledGuardianCategories = setOf(category),
                    applicationPackage = "org.quickping",
                ).configJson,
            ).getJSONObject("route").getJSONArray("rules")
            assertTrue("Guardian category $category has no reject rule", (0 until rules.length()).any { index ->
                val rule = rules.getJSONObject(index)
                rule.optString("action") == "reject" && (rule.optJSONArray("domain_suffix")?.length() ?: 0) > 0
            })
        }
    }

    private val providerConfig = """
        {
          "inbounds": [
            {"type":"tun","address":["10.0.0.1/32"],"auto_route":false},
            {"type":"mixed","tag":"provider-api","listen":"127.0.0.1","listen_port":9090}
          ],
          "outbounds": [
            {"type":"selector","tag":"proxy","outbounds":["selected"]},
            {"type":"vless","tag":"selected","server":"vpn.example.com","server_port":443,"uuid":"id"},
            {"type":"direct","tag":"direct"}
          ],
          "dns": {"servers":[{"type":"udp","tag":"provider-dns","server":"1.1.1.1"}]},
          "route": {
            "final":"selected",
            "rule_set":[{"type":"remote","tag":"geo","url":"https://example.com/rules.srs"}],
            "rules":[{"rule_set":"geo","outbound":"proxy"}]
          },
          "experimental": {"clash_api":{"external_controller":"127.0.0.1:9090"}}
        }
    """.trimIndent()

    private val legacyProviderConfig = """
        {
          "outbounds": [
            {"type":"vless","tag":"selected","server":"vpn.example.com","server_port":443,"uuid":"id"},
            {"type":"direct","tag":"direct","override_address":"1.1.1.1","override_port":53},
            {"type":"block","tag":"block"},
            {"type":"dns","tag":"dns-out"}
          ],
          "route": {
            "final":"selected",
            "rules":[
              {"domain_suffix":["ads.example"],"outbound":"block"},
              {"protocol":"dns","outbound":"dns-out"}
            ]
          }
        }
    """.trimIndent()

    private val legacyWireGuardConfig = """
        {
          "outbounds": [
            {
              "type":"wireguard",
              "tag":"wg-selected",
              "server":"wg.example.com",
              "server_port":51820,
              "local_address":["10.20.0.2/32"],
              "private_key":"private-key",
              "peer_public_key":"peer-public-key",
              "pre_shared_key":"pre-shared-key",
              "reserved":[0,0,0],
              "mtu":1408
            }
          ],
          "route": {"final":"wg-selected"}
        }
    """.trimIndent()
}
