package org.quickping.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.quickping.app.model.Server

class StableServerPingTest {
    @Test
    fun cachedPing_survivesBootstrapObjectReplacement() {
        val previous = server(id = "de-1", host = "de.example.com", port = 443, pingMs = 72)
        val refreshed = previous.copy(pingMs = null)
        val cache = mapOf(stablePingEndpointKey(previous) to 72)

        val merged = mergeStablePingValues(listOf(refreshed), cache)

        assertEquals(72, merged.single().pingMs)
    }

    @Test
    fun freshPing_overridesOlderCachedValue() {
        val server = server(id = "de-1", host = "de.example.com", port = 443, pingMs = 51)
        val cache = mapOf(stablePingEndpointKey(server) to 72)

        val merged = mergeStablePingValues(listOf(server), cache)

        assertEquals(51, merged.single().pingMs)
    }

    @Test
    fun cacheIsNotReused_whenEndpointChanges() {
        val oldServer = server(id = "de-1", host = "old.example.com", port = 443, pingMs = 72)
        val newServer = oldServer.copy(host = "new.example.com", pingMs = null)
        val cache = mapOf(stablePingEndpointKey(oldServer) to 72)

        val merged = mergeStablePingValues(listOf(newServer), cache)

        assertNull(merged.single().pingMs)
    }

    private fun server(id: String, host: String, port: Int, pingMs: Int?) = Server(
        id = id,
        countryCode = "de",
        countryName = "Germany",
        title = "Germany",
        host = host,
        port = port,
        pingMs = pingMs,
    )
}
