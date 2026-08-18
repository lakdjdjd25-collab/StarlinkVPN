package org.quickping.app.data.traffic

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualTrafficRuntimeRegistryTest {
    @After
    fun tearDown() {
        ManualTrafficRuntimeRegistry.clear()
    }

    @Test
    fun unmeteredManualTunnel_doesNotRequireTrafficMonitor() {
        ManualTrafficRuntimeRegistry.replace(session(countTraffic = false))

        assertNotNull(ManualTrafficRuntimeRegistry.take())
        assertFalse(ManualTrafficRuntimeRegistry.trafficMonitoringRequired())
    }

    @Test
    fun meteredManualTunnel_requiresTrafficMonitor() {
        ManualTrafficRuntimeRegistry.replace(session(countTraffic = true))

        assertNotNull(ManualTrafficRuntimeRegistry.take())
        assertTrue(ManualTrafficRuntimeRegistry.trafficMonitoringRequired())
    }

    @Test
    fun clear_resetsTrafficMonitorRequirement() {
        ManualTrafficRuntimeRegistry.replace(session(countTraffic = true))
        ManualTrafficRuntimeRegistry.take()
        ManualTrafficRuntimeRegistry.clear()

        assertFalse(ManualTrafficRuntimeRegistry.trafficMonitoringRequired())
    }

    private fun session(countTraffic: Boolean) = PendingManualTrafficSession(
        sessionId = "session-1",
        serviceId = "service-1",
        serverId = "manual-1",
        remainingBytes = 10_000L,
        countTraffic = countTraffic,
    )
}
