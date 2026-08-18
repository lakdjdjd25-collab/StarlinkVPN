package org.quickping.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.model.Server
import org.quickping.app.model.ServerPingState

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

    @Test
    fun lockedVip_dropsFreshAndCachedPing() {
        val locked = server(id = "vip-1", host = "", port = 0, pingMs = 18).copy(
            accessTier = "VIP",
            requiresVip = true,
            locked = true,
            canConnect = false,
        )
        val cache = mapOf(stablePingEndpointKey(locked) to 18)

        val merged = mergeStablePingValues(listOf(locked), cache)

        assertNull(merged.single().pingMs)
    }

    @Test
    fun median_usesStableSuccessfulSample() {
        assertEquals(42, medianSuccessfulPing(listOf(95, 8, 42)))
        assertEquals(25, medianSuccessfulPing(listOf(10, 40)))
        assertNull(medianSuccessfulPing(emptyList()))
    }

    @Test
    fun timeout_clearsOldLatencyInsteadOfShowingStalePing() {
        val server = server(id = "nl-1", host = "nl.example.com", port = 443, pingMs = 12)
        val snapshots = mapOf(
            stablePingEndpointKey(server) to StablePingSnapshot(ServerPingState.TIMEOUT),
        )

        val merged = applyStablePingSnapshots(
            servers = listOf(server),
            snapshots = snapshots,
            selectedServerId = "",
            connectionStatus = ConnectionStatus.Disconnected,
        ).single()

        assertNull(merged.pingMs)
        assertEquals(ServerPingState.TIMEOUT, merged.pingState)
    }

    @Test
    fun connectedServer_isNeverDowngradedToTimeoutByProbeFailure() {
        val server = server(id = "gb-1", host = "gb.example.com", port = 443, pingMs = null)
        val snapshots = mapOf(
            stablePingEndpointKey(server) to StablePingSnapshot(ServerPingState.TIMEOUT),
        )

        val merged = applyStablePingSnapshots(
            servers = listOf(server),
            snapshots = snapshots,
            selectedServerId = server.id,
            connectionStatus = ConnectionStatus.Connected,
        ).single()

        assertNull(merged.pingMs)
        assertEquals(ServerPingState.CONNECTED, merged.pingState)
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
