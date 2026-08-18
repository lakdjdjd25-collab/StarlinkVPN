package org.quickping.app.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.quickping.app.model.Server

class VipRevocationRecoveryPolicyTest {
    private fun server(id: String, vip: Boolean, selectable: Boolean) = Server(
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
    fun `revoked VIP falls back to another standard server`() {
        val failed = "france"
        val servers = listOf(
            server(failed, vip = true, selectable = false),
            server("uk", vip = false, selectable = true),
            server("vip-2", vip = true, selectable = false),
        )
        assertEquals("uk", vipRevocationFallbackServerId(failed, servers))
    }

    @Test
    fun `failed stale node is never retried as its own fallback`() {
        val failed = "france"
        val servers = listOf(server(failed, vip = false, selectable = true))
        assertNull(vipRevocationFallbackServerId(failed, servers))
    }
}
