package org.quickping.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnProbePolicyTest {
    @Test
    fun normalConnectionRequiresAllReleaseDestinations() {
        assertEquals(
            setOf("web", "telegram", "youtube", "instagram"),
            requiredConnectivityProbeNames(
                guardianEnabled = false,
                enabledGuardianCategories = emptySet(),
            ),
        )
    }

    @Test
    fun socialGuardianOnlyRemovesDestinationsItIntentionallyBlocks() {
        assertEquals(
            setOf("web", "youtube"),
            requiredConnectivityProbeNames(
                guardianEnabled = true,
                enabledGuardianCategories = setOf("socials"),
            ),
        )
    }

    @Test
    fun unrelatedGuardianCategoriesDoNotWeakenConnectivityProof() {
        assertEquals(
            setOf("web", "telegram", "youtube", "instagram"),
            requiredConnectivityProbeNames(
                guardianEnabled = true,
                enabledGuardianCategories = setOf("malware", "phishing", "ads"),
            ),
        )
    }

    @Test
    fun disabledGuardianNeverWeakensConnectivityProofEvenWithStoredSocials() {
        assertEquals(
            setOf("web", "telegram", "youtube", "instagram"),
            requiredConnectivityProbeNames(
                guardianEnabled = false,
                enabledGuardianCategories = setOf("socials"),
            ),
        )
    }
}
