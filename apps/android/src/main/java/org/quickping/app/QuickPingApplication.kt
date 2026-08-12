package org.quickping.app

import android.app.Application
import org.quickping.app.data.QuickPingRepository

class QuickPingApplication : Application() {
    val repository: QuickPingRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        QuickPingRepository(this)
    }
}
