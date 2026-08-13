package org.quickping.app.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import org.quickping.app.data.settings.QuickPingSettingsStore
import org.quickping.app.vpn.QuickPingVpnService
import org.quickping.app.vpn.VpnRuntimeStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val settings = QuickPingSettingsStore(context).load()
        if (!settings.autoConnect || !VpnRuntimeStore(context).isReady()) return
        if (!settings.proxyModeEnabled && VpnService.prepare(context) != null) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, QuickPingVpnService::class.java).setAction(QuickPingVpnService.ACTION_CONNECT),
        )
    }
}
