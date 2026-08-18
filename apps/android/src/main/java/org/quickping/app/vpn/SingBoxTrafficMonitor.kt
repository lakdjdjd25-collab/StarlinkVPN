package org.quickping.app.vpn

import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator

internal class SingBoxTrafficMonitor(
    private val onTotals: (uploadedBytes: Long, downloadedBytes: Long) -> Unit,
) : CommandClientHandler {
    private var client: CommandClient? = null

    fun start() {
        check(client == null) { "sing-box traffic monitor is already running" }
        val options = CommandClientOptions().apply {
            addCommand(Libbox.CommandStatus)
            statusInterval = STATUS_INTERVAL_NANOS
        }
        val candidate = CommandClient(this, options)
        candidate.connect()
        client = candidate
    }

    fun stop() {
        val current = client ?: return
        client = null
        runCatching { current.disconnect() }
    }

    override fun connected() = Unit

    override fun disconnected(message: String?) = Unit

    override fun setDefaultLogLevel(level: Int) = Unit

    override fun clearLogs() = Unit

    override fun writeLogs(messageList: LogIterator?) = Unit

    override fun writeStatus(message: StatusMessage) {
        onTotals(
            message.uplinkTotal.coerceAtLeast(0L),
            message.downlinkTotal.coerceAtLeast(0L),
        )
    }

    override fun writeGroups(message: OutboundGroupIterator?) = Unit

    override fun initializeClashMode(modeList: StringIterator, currentMode: String) = Unit

    override fun updateClashMode(newMode: String) = Unit

    override fun writeConnectionEvents(events: ConnectionEvents?) = Unit

    private companion object {
        const val STATUS_INTERVAL_NANOS = 1_000_000_000L
    }
}
