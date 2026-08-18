package org.quickping.app.state

import org.junit.Assert.assertEquals
import org.junit.Test
import org.quickping.app.model.Server

class VipSelectionFallbackTest {
    private fun server(id: String, vip: Boolean = false, selectable: Boolean = true) = Server(
        id = id,
        countryCode = "de",
        countryName = "Germany",
        title = id,
        accessTier = if (vip) "VIP" else "STANDARD",
        requiresVip = vip,
        locked = !selectable,
        canConnect = selectable,
    )

    @Test
    fun `VIP revocation falls back from locked VIP to standard server`() {
        val servers = listOf(
            server("vip", vip = true, selectable = false),
            server("standard", selectable = true),
        )
        assertEquals("standard", resolveSelectedServerId("vip", servers))
    }

    @Test
    fun `standard selection remains available without VIP entitlement`() {
        val servers = listOf(
            server("standard", selectable = true),
            server("vip", vip = true, selectable = false),
        )
        assertEquals("standard", resolveSelectedServerId("standard", servers))
    }
}
