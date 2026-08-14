package org.quickping.app.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.quickping.app.model.AppSettings
import org.quickping.app.model.DnsProvider

class VpnRegressionPolicyTest {
    @Test
    fun `ipv4 regression profile creates a 1280 byte IPv4 only TUN`() {
        val compiled = VpnConfigCompiler.compile(
            rawConfigJson = providerConfig,
            settings = AppSettings(
                ipv6Enabled = false,
                mtu = 1280,
                blockIrDomains = false,
            ),
            enabledGuardianCategories = emptySet(),
            applicationPackage = "org.quickping",
        )

        val tun = JSONObject(compiled.configJson)
            .getJSONArray("inbounds")
            .getJSONObject(0)
        val addresses = tun.getJSONArray("address")

        assertEquals(1280, tun.getInt("mtu"))
        assertEquals(1, addresses.length())
        assertEquals("172.19.0.1/30", addresses.getString(0))
        assertFalse(addresses.getString(0).contains(":"))
    }

    @Test
    fun `final runtime sends application DNS through the selected proxy`() {
        val compiled = VpnConfigCompiler.compile(
            rawConfigJson = providerConfig,
            settings = AppSettings(
                ipv6Enabled = false,
                mtu = 1280,
                dnsProvider = DnsProvider.Default,
                blockIrDomains = false,
            ),
            enabledGuardianCategories = emptySet(),
            applicationPackage = "org.quickping",
        )
        val runtime = JSONObject(
            applyProviderDnsPolicy(
                rawConfigJson = providerConfig,
                compiledConfigJson = compiled.configJson,
                provider = DnsProvider.Default,
            ),
        )

        val dns = runtime.getJSONObject("dns")
        val secure = dns.getJSONArray("servers").getJSONObject(1)
        assertEquals("selected", secure.getString("detour"))
        assertEquals("ipv4_only", dns.getString("strategy"))
        assertEquals(
            "quickping-node-bootstrap",
            runtime.getJSONObject("route")
                .getJSONObject("default_domain_resolver")
                .getString("server"),
        )
    }

    private val providerConfig = """
        {
          "outbounds": [
            {"type":"vless","tag":"selected","server":"vpn.example.com","server_port":443,"uuid":"id"},
            {"type":"direct","tag":"direct"}
          ],
          "dns": {"servers":[{"type":"local","tag":"provider-local"}],"final":"provider-local"},
          "route": {"final":"selected","rules":[]}
        }
    """.trimIndent()
}
