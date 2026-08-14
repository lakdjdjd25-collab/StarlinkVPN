package org.quickping.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentServerStoreTest {
    @Test
    fun `connected server moves to front without duplicates`() {
        assertEquals(
            listOf("nl", "de", "us"),
            updateRecentServerIds(
                existing = listOf("de", "nl", "us"),
                connectedServerId = "nl",
                validServerIds = setOf("de", "nl", "us"),
            ),
        )
    }

    @Test
    fun `new connection keeps only three most recent valid servers`() {
        assertEquals(
            listOf("fr", "de", "nl"),
            updateRecentServerIds(
                existing = listOf("de", "nl", "us"),
                connectedServerId = "fr",
                validServerIds = setOf("fr", "de", "nl", "us"),
            ),
        )
    }

    @Test
    fun `removed provider nodes are discarded from history`() {
        assertEquals(
            listOf("de", "nl"),
            updateRecentServerIds(
                existing = listOf("old", "nl", "us"),
                connectedServerId = "de",
                validServerIds = setOf("de", "nl"),
            ),
        )
    }
}
