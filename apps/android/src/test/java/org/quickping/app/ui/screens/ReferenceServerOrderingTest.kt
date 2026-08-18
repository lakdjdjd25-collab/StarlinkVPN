package org.quickping.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import org.quickping.app.model.Server

class ReferenceServerOrderingTest {
    @Test
    fun lockedVipServers_areAlwaysPlacedAfterOpenServers() {
        val lockedManagedVip = server("managed-vip", "VIP", selectable = false, ping = null)
        val standard = server("standard", "STANDARD", selectable = true, ping = 80)
        val openVip = server("manual-vip", "VIP", selectable = true, ping = 35)

        val ordered = orderReferenceServers(
            listOf(lockedManagedVip, standard, openVip),
            fastest = false,
        )

        assertEquals(listOf("standard", "manual-vip", "managed-vip"), ordered.map { it.id })
    }

    @Test
    fun fastestMode_neverInterleavesLockedVipWithPingableServers() {
        val lockedVip = server("locked-vip", "VIP", selectable = false, ping = null)
        val slower = server("slower", "STANDARD", selectable = true, ping = 90)
        val faster = server("faster", "VIP", selectable = true, ping = 30)

        val ordered = orderReferenceServers(
            listOf(lockedVip, slower, faster),
            fastest = true,
        )

        assertEquals(listOf("faster", "slower", "locked-vip"), ordered.map { it.id })
    }

    private fun server(id: String, tier: String, selectable: Boolean, ping: Int?): Server = Server(
        id = id,
        countryCode = "de",
        countryName = "Germany",
        title = id,
        host = if (selectable) "$id.example.com" else "",
        port = if (selectable) 443 else 0,
        pingMs = ping,
        accessTier = tier,
        requiresVip = tier == "VIP",
        locked = !selectable,
        canConnect = selectable,
    )
}
