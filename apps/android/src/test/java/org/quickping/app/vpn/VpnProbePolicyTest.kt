package org.quickping.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnProbePolicyTest {
    @Test
    fun normalConnectionRequiresAllReleaseDestinations() {
        assertEquals(
            setOf("web", "telegram", "youtube", "instagram"),
            VpnProbePolicy.requiredGroupNames(emptySet()),
        )
    }

    @Test
    fun socialGuardianOnlyRemovesDestinationsItIntentionallyBlocks() {
        assertEquals(
            setOf("web", "youtube"),
            VpnProbePolicy.requiredGroupNames(setOf("socials")),
        )
    }

    @Test
    fun unrelatedGuardianCategoriesDoNotWeakenConnectivityProof() {
        assertEquals(
            setOf("web", "telegram", "youtube", "instagram"),
            VpnProbePolicy.requiredGroupNames(setOf("malware", "phishing", "ads")),
        )
    }
}
