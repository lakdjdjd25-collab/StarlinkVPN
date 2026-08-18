package org.quickping.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualServerCategoryParsingGuardTest {
    @Test
    fun `gaming and unlimited remain independent server dimensions`() {
        val server = Server(
            id = "manual",
            countryCode = "nl",
            countryName = "Netherlands",
            title = "Netherlands",
            category = "UNLIMITED",
            subcategory = "GAMING",
            volumeBytes = 25L * 1024L * 1024L * 1024L,
            serverType = "MANUAL",
        )
        assertEquals("UNLIMITED", server.category)
        assertEquals("GAMING", server.subcategory)
        assertTrue(server.isUnlimitedCategory)
        assertTrue(server.isGaming)
    }
}
