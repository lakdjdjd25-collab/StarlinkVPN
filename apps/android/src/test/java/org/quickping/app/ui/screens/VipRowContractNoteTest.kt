package org.quickping.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.quickping.app.model.Server

class VipRowContractNoteTest {
    @Test
    fun `non VIP service sees VIP server as locked while standard server stays selectable`() {
        val lockedVip = Server(
            id = "vip",
            countryCode = "fi",
            countryName = "Finland",
            title = "Finland",
            accessTier = "VIP",
            requiresVip = true,
            locked = true,
            canConnect = false,
        )
        val standard = Server(
            id = "standard",
            countryCode = "fr",
            countryName = "France",
            title = "France",
            accessTier = "STANDARD",
            locked = false,
            canConnect = true,
        )
        assertFalse(lockedVip.selectable)
        assertTrue(standard.selectable)
    }
}
