package org.quickping.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceQuotaTest {
    @Test
    fun remainingBytesAndFractionFollowActualUsage() {
        val gib = 1024L * 1024L * 1024L
        val service = Service(
            id = "service",
            name = "NimHUB",
            plan = "PasarGuard",
            license = "PG-TEST",
            totalBytes = 100L * gib,
            usedBytes = 10L * gib,
            daysLeft = 30,
            usersCount = 1,
        )

        assertEquals(90L * gib, service.remainingBytes)
        assertEquals(0.10f, service.usedFraction, 0.0001f)
    }

    @Test
    fun remainingBytesNeverBecomeNegative() {
        val service = Service(
            id = "service",
            name = "NimHUB",
            plan = "PasarGuard",
            license = "PG-TEST",
            totalBytes = 100L,
            usedBytes = 120L,
            daysLeft = 0,
            usersCount = 1,
        )

        assertEquals(0L, service.remainingBytes)
    }
}
