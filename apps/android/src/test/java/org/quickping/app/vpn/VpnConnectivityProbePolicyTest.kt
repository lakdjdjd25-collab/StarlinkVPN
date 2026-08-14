package org.quickping.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnConnectivityProbePolicyTest {
    @Test
    fun defaultConnectionRequiresAllFourDestinationClasses() {
        assertEquals(
            setOf("web", "telegram", "youtube", "instagram"),
            requiredConnectivityProbeNames(
                guardianEnabled = true,
                enabledGuardianCategories = setOf("malware", "phishing"),
            ),
        )
    }

    @Test
    fun socialGuardianOnlySkipsDestinationsItIntentionallyBlocks() {
        assertEquals(
            setOf("web", "youtube"),
            requiredConnectivityProbeNames(
                guardianEnabled = true,
                enabledGuardianCategories = setOf("malware", "phishing", "socials"),
            ),
        )
    }

    @Test
    fun disabledGuardianDoesNotRelaxConnectivityChecks() {
        assertEquals(
            setOf("web", "telegram", "youtube", "instagram"),
            requiredConnectivityProbeNames(
                guardianEnabled = false,
                enabledGuardianCategories = setOf("socials"),
            ),
        )
    }
}
