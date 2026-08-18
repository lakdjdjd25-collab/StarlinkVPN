package org.quickping.app.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.quickping.app.model.AppSettings

class ManualVlessRuntimeConfigTest {
    @Test
    fun `Reality VLESS keeps uTLS and Reality parameters through Android compiler`() {
        val raw = """
            {
              "inbounds":[{"type":"tun","tag":"tun-in","address":["172.19.0.1/30"],"auto_route":true}],
              "outbounds":[{
                "type":"vless",
                "tag":"proxy",
                "server":"reality.example.com",
                "server_port":443,
                "uuid":"11111111-1111-4111-8111-111111111111",
                "flow":"xtls-rprx-vision",
                "packet_encoding":"xudp",
                "tls":{
                  "enabled":true,
                  "server_name":"www.example.com",
                  "utls":{"enabled":true,"fingerprint":"chrome"},
                  "reality":{
                    "enabled":true,
                    "public_key":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    "short_id":"abcd"
                  }
                }
              }],
              "route":{"final":"proxy","auto_detect_interface":true}
            }
        """.trimIndent()

        val compiled = VpnConfigCompiler.compile(
            rawConfigJson = raw,
            settings = AppSettings(blockIrDomains = false),
            enabledGuardianCategories = emptySet(),
            applicationPackage = "org.quickping",
        )

        val config = JSONObject(compiled.configJson)
        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        val tls = outbound.getJSONObject("tls")
        assertEquals("vless", outbound.getString("type"))
        assertEquals("xtls-rprx-vision", outbound.getString("flow"))
        assertEquals("xudp", outbound.getString("packet_encoding"))
        assertEquals("chrome", tls.getJSONObject("utls").getString("fingerprint"))
        assertTrue(tls.getJSONObject("reality").getBoolean("enabled"))
        assertEquals("abcd", tls.getJSONObject("reality").getString("short_id"))
        assertEquals("proxy", config.getJSONObject("route").getString("final"))
    }

    @Test
    fun `websocket VLESS keeps early-data transport options through Android compiler`() {
        val raw = """
            {
              "inbounds":[{"type":"tun","tag":"tun-in","address":["172.19.0.1/30"],"auto_route":true}],
              "outbounds":[{
                "type":"vless",
                "tag":"proxy",
                "server":"ws.example.com",
                "server_port":443,
                "uuid":"11111111-1111-4111-8111-111111111111",
                "transport":{
                  "type":"ws",
                  "path":"/nimhub",
                  "headers":{"Host":"edge.example.com"},
                  "max_early_data":2048,
                  "early_data_header_name":"Sec-WebSocket-Protocol"
                },
                "tls":{"enabled":true,"server_name":"edge.example.com"}
              }],
              "route":{"final":"proxy","auto_detect_interface":true}
            }
        """.trimIndent()

        val compiled = VpnConfigCompiler.compile(
            rawConfigJson = raw,
            settings = AppSettings(blockIrDomains = false),
            enabledGuardianCategories = emptySet(),
            applicationPackage = "org.quickping",
        )

        val transport = JSONObject(compiled.configJson)
            .getJSONArray("outbounds")
            .getJSONObject(0)
            .getJSONObject("transport")
        assertEquals("ws", transport.getString("type"))
        assertEquals("/nimhub", transport.getString("path"))
        assertEquals(2048, transport.getInt("max_early_data"))
        assertEquals("Sec-WebSocket-Protocol", transport.getString("early_data_header_name"))
    }
}
