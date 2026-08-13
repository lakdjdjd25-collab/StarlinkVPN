package org.quickping.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitAddressTest {
    @Test
    fun `normalizes domains and valid IPv4 ranges`() {
        assertEquals("example.com", normalizeSplitAddress("  *.Example.COM. "))
        assertEquals("192.168.1.1", normalizeSplitAddress("192.168.001.1"))
        assertEquals("10.0.0.0/8", normalizeSplitAddress("10.0.0.0/8"))
        assertEquals("2001:db8::/32", normalizeSplitAddress("2001:DB8::/32"))
    }

    @Test
    fun `rejects invalid IP CIDR and domain inputs`() {
        listOf(
            "999.1.1.1",
            "10.0.0.0/33",
            "2001:db8::/129",
            "example.com/24",
            "-example.com",
            "example-.com",
            "localhost",
            "example..com",
            "",
        ).forEach { value -> assertNull(value, normalizeSplitAddress(value)) }
    }
}
