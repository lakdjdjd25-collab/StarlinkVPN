package org.quickping.app.data.network

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualServerBootstrapMetadataTest {
    @Test
    fun `manual gaming metadata survives bootstrap parsing`() {
        val servers = JSONArray(
            """[
                {
                  "id":"manual-gaming",
                  "countryCode":"de",
                  "location":"Germany",
                  "remarks":"Germany 1",
                  "host":"example.com",
                  "port":443,
                  "accessTier":"STANDARD",
                  "category":"UNLIMITED",
                  "subcategory":"GAMING",
                  "volumeBytes":"53687091200",
                  "serverType":"MANUAL",
                  "canConnect":true,
                  "locked":false
                }
            ]""".trimIndent(),
        ).toServers()

        assertEquals(1, servers.size)
        assertEquals("GAMING", servers.single().subcategory)
        assertEquals("UNLIMITED", servers.single().category)
        assertEquals(53_687_091_200L, servers.single().volumeBytes)
        assertTrue(servers.single().isGaming)
        assertTrue(servers.single().isUnlimitedCategory)
    }
}
