package org.quickping.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.quickping.app.model.AppSettings
import org.quickping.app.model.SplitTunnelMode

class VpnOwnPackageSplitTest {
    @Test
    fun excludeModeNeverExcludesTheNimHubPackage() {
        val result = VpnConfigCompiler.compile(
            rawConfigJson = providerConfig,
            settings = AppSettings(
                splitTunnelingEnabled = true,
                splitTunnelMode = SplitTunnelMode.Exclude,
                splitTunnelPackages = setOf(APP_PACKAGE, "com.example.direct"),
                blockIrDomains = false,
            ),
            enabledGuardianCategories = emptySet(),
            applicationPackage = APP_PACKAGE,
        )

        assertFalse(APP_PACKAGE in result.launchOptions.excludePackages)
        assertEquals(listOf("com.example.direct"), result.launchOptions.excludePackages)
    }

    @Test
    fun includeModeAlwaysIncludesTheNimHubPackage() {
        val result = VpnConfigCompiler.compile(
            rawConfigJson = providerConfig,
            settings = AppSettings(
                splitTunnelingEnabled = true,
                splitTunnelMode = SplitTunnelMode.Include,
                splitTunnelPackages = setOf("com.example.only"),
                blockIrDomains = false,
            ),
            enabledGuardianCategories = emptySet(),
            applicationPackage = APP_PACKAGE,
        )

        assertTrue(APP_PACKAGE in result.launchOptions.includePackages)
        assertTrue("com.example.only" in result.launchOptions.includePackages)
    }

    private companion object {
        const val APP_PACKAGE = "org.quickping"
        val providerConfig = """
            {
              "outbounds": [
                {"type":"vless","tag":"selected","server":"vpn.example.com","server_port":443,"uuid":"id"},
                {"type":"direct","tag":"direct"}
              ],
              "route": {"final":"selected"}
            }
        """.trimIndent()
    }
}
