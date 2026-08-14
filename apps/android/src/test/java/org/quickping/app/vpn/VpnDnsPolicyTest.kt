package org.quickping.app.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.quickping.app.model.DnsProvider

class VpnDnsPolicyTest {
    @Test
    fun defaultModeUsesLocalDnsOnlyForNodeBootstrapAndDohForApplications() {
        val raw = """{
            "dns": {
                "servers": [{"type":"local","tag":"provider-local"}],
                "final": "provider-local"
            }
        }"""
        val compiled = """{
            "dns": {
                "servers": [{"type":"local","tag":"quickping-dns"}],
                "final": "quickping-dns"
            },
            "route": {"final":"proxy"}
        }"""

        val result = applyProviderDnsPolicy(raw, compiled, DnsProvider.Default)
        val config = JSONObject(result)
        val dns = config.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        val bootstrap = servers.getJSONObject(0)
        val secure = servers.getJSONObject(1)

        assertEquals("local", bootstrap.getString("type"))
        assertEquals("quickping-node-bootstrap", bootstrap.getString("tag"))
        assertEquals("https", secure.getString("type"))
        assertEquals("quickping-tunnel-doh", secure.getString("tag"))
        assertEquals("1.1.1.1", secure.getString("server"))
        assertEquals("proxy", secure.getString("detour"))
        assertEquals("quickping-tunnel-doh", dns.getString("final"))
        assertEquals("ipv4_only", dns.getString("strategy"))

        val resolver = config.getJSONObject("route").getJSONObject("default_domain_resolver")
        assertEquals("quickping-node-bootstrap", resolver.getString("server"))
        assertEquals("ipv4_only", resolver.getString("strategy"))
    }

    @Test
    fun explicitGoogleChoiceUsesGoogleDohInsideTunnel() {
        val raw = """{"dns":{"servers":[{"type":"https","tag":"provider-dns","server":"1.1.1.1"}]}}"""
        val compiled = """{
            "dns":{"servers":[{"type":"local","tag":"old"}],"final":"old"},
            "route":{"final":"selected"}
        }"""

        val result = applyProviderDnsPolicy(raw, compiled, DnsProvider.Google)
        val secure = JSONObject(result)
            .getJSONObject("dns")
            .getJSONArray("servers")
            .getJSONObject(1)

        assertEquals("https", secure.getString("type"))
        assertEquals("8.8.8.8", secure.getString("server"))
        assertEquals("dns.google", secure.getJSONObject("tls").getString("server_name"))
        assertEquals("selected", secure.getString("detour"))
    }

    @Test
    fun malformedCompiledConfigFallsBackWithoutThrowing() {
        val result = applyProviderDnsPolicy("{}", "not-json", DnsProvider.Default)
        assertEquals("not-json", result)
    }

    @Test
    fun routeRulesRemainIntactWhenDnsPolicyIsApplied() {
        val compiled = """{
            "route": {
                "final":"selected",
                "rules":[{"protocol":"dns","action":"hijack-dns"}]
            }
        }"""

        val result = applyProviderDnsPolicy("{}", compiled, DnsProvider.Cloudflare)
        val rules = JSONObject(result).getJSONObject("route").getJSONArray("rules")

        assertTrue(rules.length() == 1)
        assertEquals("hijack-dns", rules.getJSONObject(0).getString("action"))
    }
}
