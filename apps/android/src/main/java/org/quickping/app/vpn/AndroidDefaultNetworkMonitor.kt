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

internal enum class NetworkRegistrationStrategy {
    BEST_MATCHING_WITH_HANDLER,
    REQUEST_WITH_HANDLER,
    DEFAULT_WITH_HANDLER,
    DEFAULT,
    REQUEST,
}

/**
 * Keep this aligned with sing-box-for-android's DefaultNetworkListener:
 * Android P (API 28) and newer can report the VPN itself from default-network
 * callbacks, so P-R use an explicit request and S+ uses best-matching. N/O keep
 * the platform default-network callback; M falls back to requestNetwork.
 */
internal fun networkRegistrationStrategy(apiLevel: Int): NetworkRegistrationStrategy = when {
    apiLevel >= 31 -> NetworkRegistrationStrategy.BEST_MATCHING_WITH_HANDLER
    apiLevel >= 28 -> NetworkRegistrationStrategy.REQUEST_WITH_HANDLER
    apiLevel >= 26 -> NetworkRegistrationStrategy.DEFAULT_WITH_HANDLER
    apiLevel >= 24 -> NetworkRegistrationStrategy.DEFAULT
    else -> NetworkRegistrationStrategy.REQUEST
}

internal class AndroidDefaultNetworkMonitor(
    private val connectivity: ConnectivityManager,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
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
    var defaultNetwork: Network? = selectPhysicalNetwork()
        private set

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish(selectPhysicalNetwork(network))

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) = publish(selectPhysicalNetwork(network))

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: android.net.LinkProperties,
        ) = publish(selectPhysicalNetwork(network))

        override fun onLost(network: Network) {
            if (defaultNetwork == network) publish(selectPhysicalNetwork())
        }
    }

    @Synchronized
    fun start(updateListener: InterfaceUpdateListener) {
        listener = updateListener
        if (!registered) {
            registerCallback()
            registered = true
        }
        // Android's activeNetwork becomes the VPN after VpnService.establish().
        // Never publish it as sing-box's outbound/default interface. Doing so can
        // create a self-referential route where the proxy tries to reach itself
        // through nimHUB's own TUN.
        publish(selectPhysicalNetwork())
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
        when (networkRegistrationStrategy(Build.VERSION.SDK_INT)) {
            NetworkRegistrationStrategy.BEST_MATCHING_WITH_HANDLER ->
                connectivity.registerBestMatchingNetworkCallback(request, callback, mainHandler)

            NetworkRegistrationStrategy.REQUEST_WITH_HANDLER ->
                connectivity.requestNetwork(request, callback, mainHandler)

            NetworkRegistrationStrategy.DEFAULT_WITH_HANDLER ->
                connectivity.registerDefaultNetworkCallback(callback, mainHandler)

            NetworkRegistrationStrategy.DEFAULT ->
                connectivity.registerDefaultNetworkCallback(callback)

            NetworkRegistrationStrategy.REQUEST ->
                connectivity.requestNetwork(request, callback)
        }
    }

    private fun selectPhysicalNetwork(preferred: Network? = null): Network? {
        if (preferred != null && isUsablePhysicalNetwork(preferred)) return preferred

        val active = connectivity.activeNetwork
        if (active != null && isUsablePhysicalNetwork(active)) return active

        return connectivity.allNetworks.firstOrNull(::isUsablePhysicalNetwork)
    }

    private fun isUsablePhysicalNetwork(network: Network): Boolean {
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun publish(network: Network?, attempt: Int = 0) {
        // Callback APIs on Android N/O can still report the process default VPN.
        // Re-resolve to a physical network instead of ever handing the VPN's own
        // interface back to sing-box.
        val physicalNetwork = network?.takeIf(::isUsablePhysicalNetwork) ?: selectPhysicalNetwork()
        defaultNetwork = physicalNetwork
        val updateListener = listener ?: return
        if (physicalNetwork == null) {
            updateListener.updateDefaultInterface("", -1, false, false)
            return
        }

        val properties = connectivity.getLinkProperties(physicalNetwork)
        val interfaceName = properties?.interfaceName
        val networkInterface = interfaceName?.let { runCatching { NetworkInterface.getByName(it) }.getOrNull() }
        if ((properties == null || interfaceName == null || networkInterface == null) && attempt < 10) {
            mainHandler.postDelayed({ publish(physicalNetwork, attempt + 1) }, 100L)
            return
        }
        if (interfaceName == null || networkInterface == null) {
            updateListener.updateDefaultInterface("", -1, false, false)
            return
        }

        val capabilities = connectivity.getNetworkCapabilities(physicalNetwork)
        val expensive = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        updateListener.updateDefaultInterface(interfaceName, networkInterface.index, expensive, false)
    }
}
