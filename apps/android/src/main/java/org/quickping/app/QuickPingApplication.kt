package org.quickping.app

import android.app.Application
import android.os.Build
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import org.quickping.app.data.QuickPingRepository

class QuickPingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeSingBox()
    }

    val repository: QuickPingRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        QuickPingRepository(this)
    }

    private fun initializeSingBox() {
        val baseDirectory = filesDir.apply { mkdirs() }
        val workingDirectory = (getExternalFilesDir(null) ?: baseDirectory.resolve("runtime")).apply { mkdirs() }
        val temporaryDirectory = cacheDir.apply { mkdirs() }
        Libbox.setup(
            SetupOptions().apply {
                basePath = baseDirectory.path
                workingPath = workingDirectory.path
                tempPath = temporaryDirectory.path
                fixAndroidStack = BuildConfig.DEBUG ||
                    Build.VERSION.SDK_INT in Build.VERSION_CODES.N..Build.VERSION_CODES.N_MR1 ||
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                logMaxLines = 2_000
                debug = BuildConfig.DEBUG
            },
        )
    }
}
