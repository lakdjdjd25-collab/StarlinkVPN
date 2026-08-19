package org.quickping.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.quickping.app.model.Server

class ManagedVipCompatibilityTest {
    private fun server(
        id: String,
        requiresVip: Boolean,
        locked: Boolean,
        canConnect: Boolean,
        accessTier: String = "STANDARD",
    ) = Server(
        id = id,
        countryCode = "de",
        countryName = "Germany",
        title = id,
        serverType = "MANAGED",
        accessTier = accessTier,
        requiresVip = requiresVip,
        locked = locked,
        canConnect = canConnect,
    )

    @Test
    fun `managed requiresVip metadata marks server as vip even with compatibility accessTier`() {
        val managedVip = server(
            id = "pasarguard-vip",
            requiresVip = true,
            locked = true,
            canConnect = false,
        )
        assertTrue(managedVip.isVip)
        assertFalse(managedVip.selectable)
    }

    @Test
    fun `locked managed vip is ordered after normal servers`() {
        val normal = server("normal", requiresVip = false, locked = false, canConnect = true)
        val managedVip = server("pasarguard-vip", requiresVip = true, locked = true, canConnect = false)
        assertEquals(listOf("normal", "pasarguard-vip"), orderReferenceServers(listOf(managedVip, normal), fastest = false).map { it.id })
    }

    @Test
    fun `unlocked managed vip remains vip for crown rendering`() {
        val managedVip = server(
            id = "pasarguard-vip",
            requiresVip = true,
            locked = false,
            canConnect = true,
        )
        assertTrue(managedVip.isVip)
        assertTrue(managedVip.selectable)
    }
}
