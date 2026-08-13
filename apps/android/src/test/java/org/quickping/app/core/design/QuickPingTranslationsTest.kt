package org.quickping.app.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QuickPingTranslationsTest {
    @Test
    fun `every supported secondary language translates core settings text`() {
        listOf("nl", "ar", "tr", "ru", "hi", "zh", "ur").forEach { language ->
            val settings = QuickPingTranslations.translate(language, "Settings")
            assertNotNull(language, settings)
            assertNotEquals(language, "Settings", settings)
            assertNotNull(language, QuickPingTranslations.translate(language, "Split tunneling"))
            assertNotNull(language, QuickPingTranslations.translate(language, "Guardian"))
            assertNotNull(language, QuickPingTranslations.translate(language, "Account"))
        }
    }

    @Test
    fun `dynamic selected counts are localized`() {
        assertEquals("3 geselecteerd", QuickPingTranslations.translate("nl", "3 selected"))
        assertEquals("已选择 3 项", QuickPingTranslations.translate("zh", "3 selected"))
        assertEquals("3 منتخب", QuickPingTranslations.translate("ur", "3 selected"))
    }
}
