package org.quickping.app.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.quickping.app.model.DnsProvider

class VpnDnsPolicyTest {
    @Test
    fun defaultModePreservesModernProviderDns() {
        val raw = """{
            "dns": {
                "servers": [{"type":"https","tag":"provider-dns","server":"1.1.1.1"}],
                "final": "provider-dns"
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
        val dns = JSONObject(result).getJSONObject("dns")

        assertEquals("provider-dns", dns.getString("final"))
        assertEquals("https", dns.getJSONArray("servers").getJSONObject(0).getString("type"))
        assertEquals("proxy", JSONObject(result).getJSONObject("route").getString("final"))
    }

    @Test
    fun explicitDnsChoiceKeepsCompiledDns() {
        val raw = """{"dns":{"servers":[{"type":"https","tag":"provider-dns","server":"1.1.1.1"}]}}"""
        val compiled = """{"dns":{"servers":[{"type":"https","tag":"custom","server":"8.8.8.8"}],"final":"custom"}}"""

        val result = applyProviderDnsPolicy(raw, compiled, DnsProvider.Google)

        assertEquals("custom", JSONObject(result).getJSONObject("dns").getString("final"))
    }

    @Test
    fun legacyProviderDnsFallsBackToCompiledDns() {
        val raw = """{"dns":{"servers":[{"tag":"legacy","address":"1.1.1.1"}]}}"""
        val compiled = """{"dns":{"servers":[{"type":"local","tag":"quickping-dns"}],"final":"quickping-dns"}}"""

        val result = applyProviderDnsPolicy(raw, compiled, DnsProvider.Default)
        val dns = JSONObject(result).getJSONObject("dns")

        assertEquals("quickping-dns", dns.getString("final"))
        assertTrue(dns.getJSONArray("servers").getJSONObject(0).has("type"))
    }
}
