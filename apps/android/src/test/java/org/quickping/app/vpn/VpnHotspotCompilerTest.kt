package org.quickping.app.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.quickping.app.model.AppSettings

class VpnHotspotCompilerTest {
    @Test
    fun `hotspot sharing binds mixed proxy to LAN on configured port`() {
        val config = compile(
            AppSettings(
                shareHotspot = true,
                localProxyEnabled = true,
                proxyPort = 12345,
                blockIrDomains = false,
            ),
        )
        val mixed = config.getJSONArray("inbounds").let { inbounds ->
            (0 until inbounds.length())
                .map { inbounds.getJSONObject(it) }
                .first { it.getString("type") == "mixed" }
        }

        assertEquals("0.0.0.0", mixed.getString("listen"))
        assertEquals(12345, mixed.getInt("listen_port"))
    }

    @Test
    fun `normal local proxy remains loopback only`() {
        val config = compile(
            AppSettings(
                shareHotspot = false,
                localProxyEnabled = true,
                proxyPort = 12345,
                blockIrDomains = false,
            ),
        )
        val mixed = config.getJSONArray("inbounds").let { inbounds ->
            (0 until inbounds.length())
                .map { inbounds.getJSONObject(it) }
                .first { it.getString("type") == "mixed" }
        }

        assertEquals("127.0.0.1", mixed.getString("listen"))
        assertEquals(12345, mixed.getInt("listen_port"))
    }

    private fun compile(settings: AppSettings): JSONObject = JSONObject(
        VpnConfigCompiler.compile(
            rawConfigJson = """
                {
                  "outbounds": [
                    {"type":"vless","tag":"selected","server":"vpn.example.com","server_port":443,"uuid":"id"}
                  ],
                  "route": {"final":"selected"}
                }
            """.trimIndent(),
            settings = settings,
            enabledGuardianCategories = emptySet(),
            applicationPackage = "org.quickping",
        ).configJson,
    )
}
