package org.quickping.app.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.quickping.app.model.AppSettings

class VpnRuntimeSanitizerTest {
    @Test
    fun `full tunnel removes provider direct rules and fixes direct final`() {
        val sanitized = JSONObject(
            VpnRuntimeSanitizer.sanitize(
                rawConfigJson = providerWithDirectRouting,
                settings = AppSettings(splitTunnelingEnabled = false),
            ),
        )

        val route = sanitized.getJSONObject("route")
        assertEquals("proxy", route.getString("final"))
        val rules = route.getJSONArray("rules")
        assertFalse((0 until rules.length()).any { rules.getJSONObject(it).optString("outbound") == "direct" })
        assertTrue((0 until rules.length()).any { rules.getJSONObject(it).optString("outbound") == "proxy" })
    }

    @Test
    fun `explicit split tunneling leaves provider routing untouched`() {
        val sanitized = JSONObject(
            VpnRuntimeSanitizer.sanitize(
                rawConfigJson = providerWithDirectRouting,
                settings = AppSettings(splitTunnelingEnabled = true),
            ),
        )

        val route = sanitized.getJSONObject("route")
        assertEquals("direct", route.getString("final"))
        val rules = route.getJSONArray("rules")
        assertTrue((0 until rules.length()).any { rules.getJSONObject(it).optString("outbound") == "direct" })
    }

    private val providerWithDirectRouting = """
        {
          "outbounds": [
            {"type":"vless","tag":"proxy","server":"vpn.example.com","server_port":443,"uuid":"id"},
            {"type":"direct","tag":"direct"}
          ],
          "route": {
            "final":"direct",
            "rules":[
              {"domain_suffix":["example.org"],"outbound":"direct"},
              {"domain_suffix":["example.com"],"outbound":"proxy"}
            ]
          }
        }
    """.trimIndent()
}
