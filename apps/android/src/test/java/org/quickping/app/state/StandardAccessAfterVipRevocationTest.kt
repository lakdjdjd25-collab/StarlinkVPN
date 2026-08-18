package org.quickping.app.state

import org.junit.Assert.assertEquals
import org.junit.Test
import org.quickping.app.model.Server

class StandardAccessAfterVipRevocationTest {
    @Test
    fun `locked VIP never prevents fallback to ordinary provider server`() {
        val servers = listOf(
            Server(
                id = "vip-manual",
                countryCode = "fi",
                countryName = "Finland",
                title = "Finland VIP",
                accessTier = "VIP",
                requiresVip = true,
                locked = true,
                canConnect = false,
            ),
            Server(
                id = "pasarguard-standard",
                countryCode = "de",
                countryName = "Germany",
                title = "Germany",
                accessTier = "STANDARD",
                locked = false,
                canConnect = true,
            ),
        )
        assertEquals("pasarguard-standard", resolveSelectedServerId("vip-manual", servers))
    }
}
