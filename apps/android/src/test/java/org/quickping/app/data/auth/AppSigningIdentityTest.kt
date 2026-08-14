package org.quickping.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSigningIdentityTest {
    @Test
    fun formatsSha1AsUppercaseColonSeparatedHex() {
        assertEquals(
            "00:0A:10:FF",
            formatCertificateSha1(byteArrayOf(0x00, 0x0A, 0x10, 0xFF.toByte())),
        )
    }
}
