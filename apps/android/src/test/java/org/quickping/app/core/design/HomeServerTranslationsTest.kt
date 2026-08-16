package org.quickping.app.core.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class HomeServerTranslationsTest {
    private val languages = listOf("nl", "ar", "tr", "ru", "hi", "zh", "ur")
    private val requiredKeys = listOf(
        "Connect", "Check", "Retry", "Server filters", "Recommended", "Lowest ping",
        "Free available", "Unmetered", "No server matches these filters",
        "Search server or country", "Gaming", "Close", "Germany", "United Kingdom",
        "United States", "United Arab Emirates", "Japan", "India", "Australia", "Global",
    )

    @Test
    fun allHomeAndServerKeysExistForEverySupportedNonEnglishLanguage() {
        languages.forEach { language ->
            requiredKeys.forEach { key ->
                val translated = HomeServerTranslations.translate(language, key)
                assertNotNull("$language / $key", translated)
                assertFalse("$language / $key must not be blank", translated.isNullOrBlank())
            }
        }
    }
}
