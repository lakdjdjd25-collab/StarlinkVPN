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

    @Test fun malformedLicenseIsRejected() {
        assertTrue(referenceExtractLicense("bad code with spaces").isBlank())
    }
}
