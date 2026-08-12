package org.quickping.app.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val preferences = context.getSharedPreferences("quickping", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("auto_connect", false)) return
        preferences.edit().putBoolean(EXTRA_AUTO_CONNECT, true).apply()
    }

    companion object {
        const val EXTRA_AUTO_CONNECT = "quickping_auto_connect"
    }
}
