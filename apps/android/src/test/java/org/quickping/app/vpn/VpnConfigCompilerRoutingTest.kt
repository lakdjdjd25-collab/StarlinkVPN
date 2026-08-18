package org.quickping.app.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.quickping.app.model.AppSettings

class VpnConfigCompilerRoutingTest {
    @Test
    fun `direct provider final cannot bypass selected vpn outbound`() {
        val raw = """
            {
              "inbounds":[{"type":"tun","tag":"tun-in","address":["172.19.0.1/30"],"auto_route":true}],
              "outbounds":[
                {"type":"direct","tag":"direct"},
                {"type":"vless","tag":"proxy","server":"example.com","server_port":443,"uuid":"11111111-1111-4111-8111-111111111111"}
              ],
              "route":{
                "final":"direct",
                "rules":[{"domain_suffix":["telegram.org"],"action":"route","outbound":"proxy"}]
              }
            }
        """.trimIndent()

        val compiled = VpnConfigCompiler.compile(
            rawConfigJson = raw,
            settings = AppSettings(blockIrDomains = false, guardianEnabled = false),
            enabledGuardianCategories = emptySet(),
            applicationPackage = "org.quickping",
        )

        val route = JSONObject(compiled.configJson).getJSONObject("route")
        assertEquals("proxy", route.getString("final"))
    }
}
