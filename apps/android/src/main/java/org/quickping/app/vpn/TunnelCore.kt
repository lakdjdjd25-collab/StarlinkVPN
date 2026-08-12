package org.quickping.app.vpn

/**
 * Narrow boundary around the native packet engine. The UI and Android service
 * never depend on sing-box/Xray implementation details directly.
 */
interface TunnelCore {
    suspend fun start(tunFileDescriptor: Int, configJson: String)
    suspend fun stop()
    fun isRunning(): Boolean
}

class UnavailableTunnelCore : TunnelCore {
    override suspend fun start(tunFileDescriptor: Int, configJson: String) {
        error("Native VPN core is not installed in this build variant")
    }

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = false
}
