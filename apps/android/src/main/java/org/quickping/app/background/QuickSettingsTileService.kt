package org.quickping.app.background

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.quickping.app.MainActivity
import org.quickping.app.vpn.QuickPingVpnService
import org.quickping.app.vpn.ServiceState

class QuickSettingsTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val connected = QuickPingVpnService.status.value.state == ServiceState.Connected
        if (connected) {
            startService(
                Intent(this, QuickPingVpnService::class.java)
                    .setAction(QuickPingVpnService.ACTION_DISCONNECT),
            )
        } else {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        2,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        refresh()
    }

    private fun refresh() {
        val current = QuickPingVpnService.status.value.state
        qsTile?.apply {
            state = when (current) {
                ServiceState.Connected -> Tile.STATE_ACTIVE
                ServiceState.Connecting -> Tile.STATE_UNAVAILABLE
                else -> Tile.STATE_INACTIVE
            }
            label = "NimHUB Vpn"
            updateTile()
        }
    }
}
