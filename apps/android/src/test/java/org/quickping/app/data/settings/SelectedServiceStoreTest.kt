package org.quickping.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedServiceStoreTest {
    @Test
    fun `valid persisted service is restored`() {
        assertEquals(
            "premium",
            resolveSelectedServiceId(
                preferredId = "premium",
                availableIds = listOf("free", "premium"),
            ),
        )
    }

    @Test
    fun `removed persisted service falls back to first current service`() {
        assertEquals(
            "free",
            resolveSelectedServiceId(
                preferredId = "deleted-service",
                availableIds = listOf("free", "premium"),
            ),
        )
    }

    @Test
    fun `empty service catalog resolves to empty id`() {
        assertEquals(
            "",
            resolveSelectedServiceId(
                preferredId = "premium",
                availableIds = emptyList(),
            ),
        )
    }
}
