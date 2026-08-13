/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Network selection follows the approach used by sing-box for Android, so the
 * packet engine keeps using the physical network after Android installs the
 * VPN as the process default network.
 */
package org.quickping.app.vpn

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.nekohasekai.libbox.InterfaceUpdateListener
import java.net.NetworkInterface

internal class AndroidDefaultNetworkMonitor(
    private val connectivity: ConnectivityManager,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .apply {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            }
        }
        .build()

    @Volatile
    private var listener: InterfaceUpdateListener? = null

    @Volatile
    var defaultNetwork: Network? = null
        private set

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish(network)

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) = publish(network)

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: android.net.LinkProperties,
        ) = publish(network)

        override fun onLost(network: Network) {
            if (defaultNetwork == network) publish(null)
        }
    }

    @Synchronized
    fun start(updateListener: InterfaceUpdateListener) {
        listener = updateListener
        if (!registered) {
            registerCallback()
            registered = true
        }
        publish(connectivity.activeNetwork)
    }

    @Synchronized
    @Suppress("UNUSED_PARAMETER")
    fun stop(updateListener: InterfaceUpdateListener) {
        listener = null
        defaultNetwork = null
        if (registered) {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
            registered = false
        }
    }

    @Synchronized
    fun close() {
        listener = null
        defaultNetwork = null
        if (registered) {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
            registered = false
        }
    }

    private fun registerCallback() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                connectivity.registerBestMatchingNetworkCallback(request, callback, mainHandler)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                connectivity.requestNetwork(request, callback, mainHandler)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                connectivity.registerDefaultNetworkCallback(callback, mainHandler)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ->
                connectivity.registerDefaultNetworkCallback(callback)

            else -> connectivity.requestNetwork(request, callback)
        }
    }

    private fun publish(network: Network?, attempt: Int = 0) {
        defaultNetwork = network
        val updateListener = listener ?: return
        if (network == null) {
            updateListener.updateDefaultInterface("", -1, false, false)
            return
        }

        val properties = connectivity.getLinkProperties(network)
        val interfaceName = properties?.interfaceName
        val networkInterface = interfaceName?.let { runCatching { NetworkInterface.getByName(it) }.getOrNull() }
        if ((properties == null || interfaceName == null || networkInterface == null) && attempt < 10) {
            mainHandler.postDelayed({ publish(network, attempt + 1) }, 100L)
            return
        }
        if (interfaceName == null || networkInterface == null) {
            updateListener.updateDefaultInterface("", -1, false, false)
            return
        }

        val capabilities = connectivity.getNetworkCapabilities(network)
        val expensive = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        updateListener.updateDefaultInterface(interfaceName, networkInterface.index, expensive, false)
    }
}
