package org.quickping.app.data.traffic

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualTrafficStateTest {
    @Test
    fun `local remaining subtracts unreported counted traffic`() {
        val state = ManualTrafficState(
            sessionId = "session",
            serviceId = "service",
            serverId = "server",
            confirmedRemainingBytes = 1_000L,
            confirmedAtTotalBytes = 300L,
            uploadedBytes = 450L,
            downloadedBytes = 250L,
            countTraffic = true,
            pendingFinal = false,
        )
        assertEquals(600L, state.unreportedBytes)
        assertEquals(400L, state.localRemainingBytes)
    }

    @Test
    fun `local remaining never becomes negative`() {
        val state = ManualTrafficState(
            sessionId = "session",
            serviceId = "service",
            serverId = "server",
            confirmedRemainingBytes = 100L,
            confirmedAtTotalBytes = 0L,
            uploadedBytes = 80L,
            downloadedBytes = 70L,
            countTraffic = true,
            pendingFinal = false,
        )
        assertEquals(0L, state.localRemainingBytes)
    }

    @Test
    fun `uncounted traffic does not reduce shared quota locally`() {
        val state = ManualTrafficState(
            sessionId = "session",
            serviceId = "service",
            serverId = "server",
            confirmedRemainingBytes = 900L,
            confirmedAtTotalBytes = 0L,
            uploadedBytes = 500L,
            downloadedBytes = 600L,
            countTraffic = false,
            pendingFinal = false,
        )
        assertEquals(900L, state.localRemainingBytes)
    }

    @Test
    fun `saturated total prevents long overflow`() {
        val state = ManualTrafficState(
            sessionId = "session",
            serviceId = "service",
            serverId = "server",
            confirmedRemainingBytes = Long.MAX_VALUE,
            confirmedAtTotalBytes = 0L,
            uploadedBytes = Long.MAX_VALUE,
            downloadedBytes = 10L,
            countTraffic = true,
            pendingFinal = false,
        )
        assertEquals(Long.MAX_VALUE, state.totalBytes)
    }
}
