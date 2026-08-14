package org.quickping.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkRegistrationStrategyTest {
    @Test
    fun api23UsesExplicitNetworkRequest() {
        assertEquals(
            NetworkRegistrationStrategy.REQUEST,
            networkRegistrationStrategy(23),
        )
    }

    @Test
    fun api24And25UseDefaultCallback() {
        assertEquals(NetworkRegistrationStrategy.DEFAULT, networkRegistrationStrategy(24))
        assertEquals(NetworkRegistrationStrategy.DEFAULT, networkRegistrationStrategy(25))
    }

    @Test
    fun api26And27UseDefaultCallbackWithHandler() {
        assertEquals(NetworkRegistrationStrategy.DEFAULT_WITH_HANDLER, networkRegistrationStrategy(26))
        assertEquals(NetworkRegistrationStrategy.DEFAULT_WITH_HANDLER, networkRegistrationStrategy(27))
    }

    @Test
    fun api28Through30UseExplicitRequestWithHandler() {
        assertEquals(NetworkRegistrationStrategy.REQUEST_WITH_HANDLER, networkRegistrationStrategy(28))
        assertEquals(NetworkRegistrationStrategy.REQUEST_WITH_HANDLER, networkRegistrationStrategy(30))
    }

    @Test
    fun api31AndNewerUseBestMatchingCallback() {
        assertEquals(NetworkRegistrationStrategy.BEST_MATCHING_WITH_HANDLER, networkRegistrationStrategy(31))
        assertEquals(NetworkRegistrationStrategy.BEST_MATCHING_WITH_HANDLER, networkRegistrationStrategy(36))
    }
}
