package org.quickping.app.state

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedServerPersistenceTest {
    @Test
    fun `selection id remains stable across refresh decision`() {
        val current = "france"
        val refreshed = listOf("uk", "france", "germany")
        val selected = current.takeIf { id -> refreshed.any { it == id } } ?: refreshed.firstOrNull().orEmpty()
        assertEquals("france", selected)
    }
}
