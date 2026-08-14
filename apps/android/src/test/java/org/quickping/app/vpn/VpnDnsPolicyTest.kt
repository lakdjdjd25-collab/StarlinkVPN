package org.quickping.app.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "outbounds": [{"type":"vless","tag":"proxy"}],
            "route": {"final":"proxy"}
        }"""

        val result = applyProviderDnsPolicy(raw, compiled, DnsProvider.Default)
        val dns = JSONObject(result).getJSONObject("dns")

        assertEquals("provider-dns", dns.getString("final"))
        assertEquals("https", dns.getJSONArray("servers").getJSONObject(0).getString("type"))
        assertEquals("proxy", JSONObject(result).getJSONObject("route").getString("final"))
    }

    @Test
    fun providerDnsDetouredThroughProxyFallsBackToLocalBootstrapDns() {
        val raw = """{
            "dns":{"servers":[{"type":"https","tag":"provider-dns","server":"1.1.1.1","detour":"selected-node"}],"final":"provider-dns"}
        }"""
        val compiled = """{
            "dns":{"servers":[{"type":"local","tag":"quickping-dns"}],"final":"quickping-dns"},
            "outbounds":[{"type":"vless","tag":"selected-node","server":"vpn.example.com","server_port":443}]
        }"""

        val result = applyProviderDnsPolicy(raw, compiled, DnsProvider.Default)
        val dns = JSONObject(result).getJSONObject("dns")
        val server = dns.getJSONArray("servers").getJSONObject(0)

        assertEquals("quickping-dns", dns.getString("final"))
        assertEquals("local", server.getString("type"))
        assertFalse(server.has("detour"))
    }

    @Test
    fun providerDnsWithMissingDetourFallsBackToCompiledDns() {
        val raw = """{
            "dns":{"servers":[{"type":"https","tag":"provider-dns","server":"1.1.1.1","detour":"missing-selector"}],"final":"provider-dns"}
        }"""
        val compiled = """{
            "dns":{"servers":[{"type":"local","tag":"quickping-dns"}],"final":"quickping-dns"},
            "outbounds":[{"type":"vless","tag":"selected-node"}]
        }"""

        val result = applyProviderDnsPolicy(raw, compiled, DnsProvider.Default)

        assertEquals("quickping-dns", JSONObject(result).getJSONObject("dns").getString("final"))
    }

    @Test
    fun providerDnsWithMissingDomainResolverFallsBackToCompiledDns() {
        val raw = """{
            "dns":{"servers":[{"type":"https","tag":"provider-dns","server":"dns.example","domain_resolver":"missing-resolver"}],"final":"provider-dns"}
        }"""
        val compiled = """{
            "dns":{"servers":[{"type":"local","tag":"quickping-dns"}],"final":"quickping-dns"},
            "outbounds":[{"type":"vless","tag":"selected-node"}]
        }"""

        val result = applyProviderDnsPolicy(raw, compiled, DnsProvider.Default)

        assertEquals("quickping-dns", JSONObject(result).getJSONObject("dns").getString("final"))
    }

    @Test
    fun providerDnsWithSelfContainedDomainResolverIsPreserved() {
        val raw = """{
            "dns":{"servers":[
                {"type":"local","tag":"bootstrap"},
                {"type":"https","tag":"provider-dns","server":"dns.example","domain_resolver":"bootstrap"}
            ],"final":"provider-dns"}
        }"""
        val compiled = """{
            "dns":{"servers":[{"type":"local","tag":"quickping-dns"}],"final":"quickping-dns"},
            "outbounds":[{"type":"vless","tag":"selected-node"}]
        }"""

        val result = applyProviderDnsPolicy(raw, compiled, DnsProvider.Default)

        assertEquals("provider-dns", JSONObject(result).getJSONObject("dns").getString("final"))
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
