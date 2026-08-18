package org.quickping.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualServerCategoryTest {
    @Test
    fun `Unlimited Gaming is both unlimited and gaming`() {
        val server = Server(
            id = "manual-gaming",
            countryCode = "de",
            countryName = "Germany",
            title = "Germany Gaming",
            category = "UNLIMITED",
            subcategory = "GAMING",
            serverType = "MANUAL",
        )
        assertTrue(server.isUnlimitedCategory)
        assertTrue(server.isGaming)
        assertFalse(server.isLimitedCategory)
    }

    @Test
    fun `Limited Gaming is limited and gaming`() {
        val server = Server(
            id = "manual-limited-gaming",
            countryCode = "tr",
            countryName = "Turkey",
            title = "Turkey Gaming",
            category = "LIMITED",
            subcategory = "GAMING",
            volumeBytes = 50L * 1024L * 1024L * 1024L,
            serverType = "MANUAL",
        )
        assertTrue(server.isLimitedCategory)
        assertTrue(server.isGaming)
        assertFalse(server.isUnlimitedCategory)
    }

    @Test
    fun `General Unlimited is not routed to gaming`() {
        val server = Server(
            id = "manual-general",
            countryCode = "nl",
            countryName = "Netherlands",
            title = "Netherlands",
            category = "UNLIMITED",
            subcategory = "GENERAL",
            serverType = "MANUAL",
        )
        assertTrue(server.isUnlimitedCategory)
        assertFalse(server.isGaming)
    }

    @Test
    fun `legacy category gaming remains compatible`() {
        val server = Server(
            id = "legacy-gaming",
            countryCode = "fr",
            countryName = "France",
            title = "France",
            category = "GAMING",
        )
        assertTrue(server.isGaming)
    }
}
