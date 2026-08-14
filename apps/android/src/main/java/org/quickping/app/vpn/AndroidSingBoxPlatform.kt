/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Portions of this Android platform bridge are adapted from SagerNet's
 * sing-box-for-android project (GPL-3.0-or-later).
 */
package org.quickping.app.vpn

import android.content.pm.PackageManager.NameNotFoundException
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.NetworkCapabilities
import android.net.ProxyInfo
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.OsConstants
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.security.KeyStore
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

internal class AndroidSingBoxPlatform(
    private val service: VpnService,
) : PlatformInterface {
    private val connectivity = service.getSystemService<ConnectivityManager>()
        ?: error("ConnectivityManager is unavailable")
    private val networkMonitor = AndroidDefaultNetworkMonitor(connectivity)

    @Volatile
    private var tunInterface: ParcelFileDescriptor? = null

    fun hasTunInterface(): Boolean = tunInterface?.fileDescriptor?.valid() == true

    override fun localDNSTransport(): LocalDNSTransport = AndroidLocalDnsTransport(networkMonitor)

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        check(service.protect(fd)) { "android: failed to protect outbound socket" }
    }

    override fun openTun(options: TunOptions): Int {
        check(VpnService.prepare(service) == null) { "android: missing VPN permission" }

        val builder = service.Builder()
            .setSession("nimHUB")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        // Libbox iterators are single-use. Keep the address-family presence before
        // consuming the address iterators; Android 13+ needs these booleans when
        // sing-box does not emit an explicit route-address list and we must add
        // the full-tunnel default route ourselves.
        val hasIpv4Address = options.inet4Address.hasNext()
        val hasIpv6Address = options.inet6Address.hasNext()
        options.inet4Address.consume { builder.addAddress(it.address(), it.prefix()) }
        options.inet6Address.consume { builder.addAddress(it.address(), it.prefix()) }

        if (options.autoRoute) {
            builder.addDnsServer(options.dnsServerAddress.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addModernRoutes(builder, options, hasIpv4Address, hasIpv6Address)
            } else {
                options.inet4RouteRange.consume { builder.addRoute(it.address(), it.prefix()) }
                options.inet6RouteRange.consume { builder.addRoute(it.address(), it.prefix()) }
            }
            applyPerAppRules(builder, options)
        }

        if (options.isHTTPProxyEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setHttpProxy(
                ProxyInfo.buildDirectProxy(
                    options.httpProxyServer,
                    options.httpProxyServerPort,
                    options.httpProxyBypassDomain.asList(),
                ),
            )
        }

        val descriptor = builder.establish()
            ?: error("android: VPN interface creation was rejected")
        tunInterface?.close()
        tunInterface = descriptor
        return descriptor.fd
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun addModernRoutes(
        builder: VpnService.Builder,
        options: TunOptions,
        hasIpv4Address: Boolean,
        hasIpv6Address: Boolean,
    ) {
        val inet4Routes = options.inet4RouteAddress
        if (inet4Routes.hasNext()) {
            inet4Routes.consume { builder.addRoute(IpPrefix(InetAddress.getByName(it.address()), it.prefix())) }
        } else if (hasIpv4Address) {
            builder.addRoute("0.0.0.0", 0)
        }

        val inet6Routes = options.inet6RouteAddress
        if (inet6Routes.hasNext()) {
            inet6Routes.consume { builder.addRoute(IpPrefix(InetAddress.getByName(it.address()), it.prefix())) }
        } else if (hasIpv6Address) {
            builder.addRoute("::", 0)
        }

        options.inet4RouteExcludeAddress.consume {
            builder.excludeRoute(IpPrefix(InetAddress.getByName(it.address()), it.prefix()))
        }
        options.inet6RouteExcludeAddress.consume {
            builder.excludeRoute(IpPrefix(InetAddress.getByName(it.address()), it.prefix()))
        }
    }

    private fun applyPerAppRules(builder: VpnService.Builder, options: TunOptions) {
        options.includePackage.consumeStrings { packageName ->
            try {
                builder.addAllowedApplication(packageName)
            } catch (error: NameNotFoundException) {
                Log.w(TAG, "Ignoring missing included package: $packageName", error)
            }
        }
        options.excludePackage.consumeStrings { packageName ->
            try {
                builder.addDisallowedApplication(packageName)
            } catch (error: NameNotFoundException) {
                Log.w(TAG, "Ignoring missing excluded package: $packageName", error)
            }
        }
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        val uid = connectivity.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort),
        )
        check(uid != Process.INVALID_UID) { "android: connection owner not found" }
        val packages = service.packageManager.getPackagesForUid(uid).orEmpty()
        return ConnectionOwner().also { owner ->
            owner.userId = uid
            owner.userName = packages.firstOrNull().orEmpty()
            owner.setAndroidPackageNames(StringArray(packages.toList()))
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        networkMonitor.start(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        networkMonitor.stop(listener)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val javaInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val result = mutableListOf<LibboxNetworkInterface>()
        for (network in connectivity.allNetworks) {
            val linkProperties = connectivity.getLinkProperties(network) ?: continue
            val capabilities = connectivity.getNetworkCapabilities(network) ?: continue

            // Never expose nimHUB's own VPN (or another VPN transport) as an
            // upstream candidate to sing-box. Outbound sockets are protected by
            // VpnService.protect(), and interface discovery must agree with that
            // policy or auto-detection can still point the core back at the TUN.
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            ) {
                continue
            }

            val interfaceName = linkProperties.interfaceName ?: continue
            val javaInterface = javaInterfaces.firstOrNull { it.name == interfaceName } ?: continue
            result += LibboxNetworkInterface().also { item ->
                item.name = interfaceName
                item.index = javaInterface.index
                item.mtu = runCatching { javaInterface.mtu }.getOrDefault(1500)
                item.addresses = StringArray(javaInterface.interfaceAddresses.map { it.toPrefix() })
                item.dnsServer = StringArray(linkProperties.dnsServers.mapNotNull { it.hostAddress })
                item.type = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
                item.flags = javaInterface.toPlatformFlags(capabilities)
                item.metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        }
        return InterfaceArray(result.iterator())
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun readWIFIState(): WIFIState? = runCatching {
        @Suppress("DEPRECATION")
        val wifiInfo = service.applicationContext.getSystemService<WifiManager>()?.connectionInfo
            ?: return@runCatching null
        var ssid = wifiInfo.ssid ?: return@runCatching null
        if (ssid == "<unknown ssid>") return@runCatching WIFIState("", "")
        if (ssid.startsWith('"') && ssid.endsWith('"')) ssid = ssid.substring(1, ssid.length - 1)
        WIFIState(ssid, wifiInfo.bssid.orEmpty())
    }.getOrNull()

    override fun systemCertificates(): StringIterator = StringArray(systemCertificatePem)

    override fun clearDNSCache() = Unit

    override fun sendNotification(notification: Notification) {
        Log.i(TAG, "sing-box notification: ${notification.title}: ${notification.body}")
    }

    fun close() {
        tunInterface?.close()
        tunInterface = null
        networkMonitor.close()
    }

    private class InterfaceArray(
        private val iterator: Iterator<LibboxNetworkInterface>,
    ) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): LibboxNetworkInterface = iterator.next()
    }

    internal class StringArray(values: Collection<String>) : StringIterator {
        private val values = values.toList()
        private var index = 0

        override fun len(): Int = values.size - index
        override fun hasNext(): Boolean = index < values.size
        override fun next(): String = values[index++]
    }

    private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
        "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
    } else {
        "${address.hostAddress}/$networkPrefixLength"
    }

    private fun NetworkInterface.toPlatformFlags(capabilities: NetworkCapabilities): Int {
        var flags = 0
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
        }
        if (isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
        if (isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
        if (supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
        return flags
    }

    private companion object {
        const val TAG = "nimHUBSingBox"

        val systemCertificatePem: List<String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val result = mutableListOf<String>()
            runCatching {
                val keyStore = KeyStore.getInstance("AndroidCAStore")
                keyStore.load(null, null)
                val aliases = keyStore.aliases()
                while (aliases.hasMoreElements()) {
                    val certificate = keyStore.getCertificate(aliases.nextElement()) ?: continue
                    val body = Base64.encodeToString(certificate.encoded, Base64.NO_WRAP)
                        .chunked(64)
                        .joinToString("\n")
                    result += "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----"
                }
            }.onFailure { Log.w(TAG, "Unable to read Android CA store", it) }
            result
        }
    }
}

private inline fun io.nekohasekai.libbox.RoutePrefixIterator.consume(
    block: (io.nekohasekai.libbox.RoutePrefix) -> Unit,
) {
    while (hasNext()) block(next())
}

private inline fun StringIterator.consumeStrings(block: (String) -> Unit) {
    while (hasNext()) block(next())
}

private fun StringIterator.asList(): List<String> = buildList {
    while (this@asList.hasNext()) add(this@asList.next())
}
