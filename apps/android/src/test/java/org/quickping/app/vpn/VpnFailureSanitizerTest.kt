package org.quickping.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnFailureSanitizerTest {
    @Test
    fun redactsUrlsCredentialsUuidEmailAndLongSecrets() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        val token = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN0123456789"
        val raw = "dial https://vpn.example.com/path?token=abc uuid=\"$uuid\" password: hunter2 " +
            "Authorization: Bearer abc.def.ghi user=test@example.com secret=$token"

        val sanitized = sanitizeVpnFailureDetail(raw)

        assertTrue("[url]" in sanitized)
        assertTrue("password=[redacted]" in sanitized)
        assertTrue("uuid=[redacted]" in sanitized || "[uuid]" in sanitized)
        assertTrue("[email]" in sanitized)
        assertFalse("hunter2" in sanitized)
        assertFalse(uuid in sanitized)
        assertFalse("test@example.com" in sanitized)
        assertFalse(token in sanitized)
        assertFalse("abc.def.ghi" in sanitized)
    }

    @Test
    fun keepsUsefulNetworkFailureContext() {
        val sanitized = sanitizeVpnFailureDetail("dial tcp edge.example.net:443: i/o timeout")

        assertTrue("edge.example.net:443" in sanitized)
        assertTrue("timeout" in sanitized)
    }

    @Test
    fun usesSafeFallbackForBlankDetail() {
        assertTrue(sanitizeVpnFailureDetail("", "IllegalStateException") == "IllegalStateException")
    }
}
