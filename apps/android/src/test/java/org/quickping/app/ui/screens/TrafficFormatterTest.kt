package org.quickping.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficFormatterTest {
    @Test
    fun formatsBytesAcrossUnits() {
        assertEquals("0B", formatCompactBytes(0))
        assertEquals("1023B", formatCompactBytes(1023))
        assertEquals("1.0KB", formatCompactBytes(1024))
        assertEquals("1.5KB", formatCompactBytes(1536))
        assertEquals("1.0MB", formatCompactBytes(1024L * 1024L))
        assertEquals("1.0GB", formatCompactBytes(1024L * 1024L * 1024L))
    }

    @Test
    fun negativeCountersAreClamped() {
        assertEquals("0B", formatCompactBytes(-1))
    }
}
