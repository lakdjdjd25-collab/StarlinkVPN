package org.quickping.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceLoginLicenseTest {
    @Test fun plainLicenseIsNormalized() {
        assertEquals("ABC123-XY", referenceExtractLicense("  abc123-xy  "))
    }

    @Test fun prefixedLicenseIsNormalized() {
        assertEquals("ABC123-XY", referenceExtractLicense("license: abc123-xy"))
    }

    @Test fun canonicalNimHubQrPayloadIsAccepted() {
        assertEquals("NH-ABCD-2345-EFGH-6789", referenceExtractLicense("NIMHUB:NH-ABCD-2345-EFGH-6789"))
    }

    @Test fun jsonQrPayloadIsAccepted() {
        assertEquals("NH-ABCD-2345-EFGH-6789", referenceExtractLicense("{\"license\":\"nh-abcd-2345-efgh-6789\"}"))
    }

    @Test fun linkQrPayloadIsAccepted() {
        assertEquals("NH-ABCD-2345-EFGH-6789", referenceExtractLicense("https://nimhub.example/login?license=nh-abcd-2345-efgh-6789"))
    }

    @Test fun unrelatedLinkIsRejected() {
        assertTrue(referenceExtractLicense("https://example.com/login?license=nh-abcd-2345-efgh-6789").isBlank())
    }

    @Test fun unsafeSchemeIsRejected() {
        assertTrue(referenceExtractLicense("javascript://nimhub?license=nh-abcd-2345-efgh-6789").isBlank())
    }

    @Test fun malformedLicenseIsRejected() {
        assertTrue(referenceExtractLicense("bad code with spaces").isBlank())
    }
}
