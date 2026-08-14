package org.quickping.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnTrafficProofTest {
    @Test
    fun requiresANativeBaselineSample() {
        val baseline = VpnTrafficStats(uplinkTotalBytes = 100, downlinkTotalBytes = 200, sampleSequence = 0)
        val current = VpnTrafficStats(uplinkTotalBytes = 120, downlinkTotalBytes = 240, sampleSequence = 1)

        assertFalse(hasNativeTrafficAdvanced(baseline, current))
    }

    @Test
    fun requiresANewerStatusSample() {
        val baseline = VpnTrafficStats(uplinkTotalBytes = 100, downlinkTotalBytes = 200, sampleSequence = 4)
        val current = VpnTrafficStats(uplinkTotalBytes = 120, downlinkTotalBytes = 240, sampleSequence = 4)

        assertFalse(hasNativeTrafficAdvanced(baseline, current))
    }

    @Test
    fun rejectsOnlyUploadOrOnlyDownloadGrowth() {
        val baseline = VpnTrafficStats(uplinkTotalBytes = 100, downlinkTotalBytes = 200, sampleSequence = 4)

        assertFalse(
            hasNativeTrafficAdvanced(
                baseline,
                VpnTrafficStats(uplinkTotalBytes = 120, downlinkTotalBytes = 200, sampleSequence = 5),
            ),
        )
        assertFalse(
            hasNativeTrafficAdvanced(
                baseline,
                VpnTrafficStats(uplinkTotalBytes = 100, downlinkTotalBytes = 240, sampleSequence = 5),
            ),
        )
    }

    @Test
    fun acceptsBidirectionalGrowthFromANewerSample() {
        val baseline = VpnTrafficStats(uplinkTotalBytes = 100, downlinkTotalBytes = 200, sampleSequence = 4)
        val current = VpnTrafficStats(uplinkTotalBytes = 120, downlinkTotalBytes = 240, sampleSequence = 5)

        assertTrue(hasNativeTrafficAdvanced(baseline, current))
    }
}
