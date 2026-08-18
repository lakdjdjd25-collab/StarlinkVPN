/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.quickping.app.vpn

import android.util.Log
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SystemProxyStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.quickping.app.data.traffic.ManualTrafficRuntimeRegistry

internal interface TunnelCore {
    suspend fun start(configJson: String, launchOptions: TunnelLaunchOptions = TunnelLaunchOptions())
    suspend fun stop()
    fun isRunning(): Boolean
}

internal class SingBoxTunnelCore(
    private val platform: AndroidSingBoxPlatform,
    private val onNativeStop: () -> Unit,
    private val onTrafficTotals: (uploadedBytes: Long, downloadedBytes: Long) -> Unit = { _, _ -> },
    private val trafficMonitoringRequired: () -> Boolean = {
        ManualTrafficRuntimeRegistry.trafficMonitoringRequired()
    },
) : TunnelCore, CommandServerHandler {
    private val lifecycle = Mutex()

    @Volatile
    private var commandServer: CommandServer? = null

    @Volatile
    private var trafficMonitor: SingBoxTrafficMonitor? = null

    override suspend fun start(configJson: String, launchOptions: TunnelLaunchOptions) = lifecycle.withLock {
        require(configJson.isNotBlank()) { "Missing runtime configuration" }
        check(commandServer == null) { "sing-box is already running" }
        Libbox.checkConfig(configJson)

        val candidate = CommandServer(this, platform)
        val monitor = SingBoxTrafficMonitor(onTrafficTotals)
        try {
            candidate.start()
            candidate.startOrReloadService(
                configJson,
                OverrideOptions().apply {
                    autoRedirect = false
                    if (launchOptions.includePackages.isNotEmpty()) {
                        includePackage = AndroidSingBoxPlatform.StringArray(launchOptions.includePackages)
                    }
                    if (launchOptions.excludePackages.isNotEmpty()) {
                        excludePackage = AndroidSingBoxPlatform.StringArray(launchOptions.excludePackages)
                    }
                },
            )

            var monitoringStarted = false
            try {
                monitor.startWithRetry()
                monitoringStarted = true
            } catch (error: Throwable) {
                if (trafficMonitoringRequired()) {
                    throw IllegalStateException("manual traffic monitor unavailable", error)
                }
                Log.w(TAG, "Traffic monitor unavailable for non-manual tunnel; continuing without accounting", error)
            }
            if (monitoringStarted) trafficMonitor = monitor
            commandServer = candidate
        } catch (error: Throwable) {
            monitor.stop()
            runCatching { candidate.closeService() }
            runCatching { candidate.close() }
            platform.close()
            throw error
        }
    }

    override suspend fun stop() = lifecycle.withLock {
        trafficMonitor?.stop()
        trafficMonitor = null
        val current = commandServer ?: run {
            platform.close()
            return@withLock
        }
        commandServer = null
        platform.close()
        runCatching { current.closeService() }
            .onFailure { Log.w(TAG, "Unable to close sing-box service cleanly", it) }
        runCatching { current.close() }
            .onFailure { Log.w(TAG, "Unable to close sing-box command server cleanly", it) }
    }

    override fun isRunning(): Boolean = commandServer != null

    override fun serviceStop() = onNativeStop()

    override fun serviceReload() {
        Log.i(TAG, "Ignoring an unsolicited native reload request")
    }

    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus().apply {
        available = false
        enabled = false
    }

    override fun setSystemProxyEnabled(enabled: Boolean) {
        Log.i(TAG, "Android system proxy switching is not enabled")
    }

    override fun writeDebugMessage(message: String?) {
        Log.d(TAG, message.orEmpty())
    }

    private companion object {
        const val TAG = "QuickPingSingBox"
    }
}
