package org.quickping.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VipServerPolicyTest {
    @Test
    fun `VIP wins only when latency is effectively tied`() {
        val standard = Server("standard", "de", "Germany", "Germany", pingMs = 42)
        val vip = Server("vip", "nl", "Netherlands", "Netherlands", pingMs = 49, accessTier = "VIP")
        assertEquals("vip", selectBestServerForAuto(listOf(standard, vip))?.id)
    }

    @Test
    fun `much faster STANDARD server still wins`() {
        val standard = Server("standard", "de", "Germany", "Germany", pingMs = 30)
        val vip = Server("vip", "nl", "Netherlands", "Netherlands", pingMs = 60, accessTier = "VIP")
        assertEquals("standard", selectBestServerForAuto(listOf(standard, vip))?.id)
    }

    @Test
    fun `first available server is used before ping results arrive`() {
        val first = Server("first", "de", "Germany", "Germany")
        val second = Server("second", "nl", "Netherlands", "Netherlands", accessTier = "VIP")
        assertEquals("first", selectBestServerForAuto(listOf(first, second))?.id)
    }
}
