package org.quickping.app.vpn

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import org.json.JSONObject

internal class VpnRuntimeStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "vpn-runtime")
    private val runtimeFile = File(directory, "selected-config.json")

    fun write(configJson: String) {
        require(configJson.toByteArray(StandardCharsets.UTF_8).size.toLong() <= MAX_CONFIG_BYTES) {
            "Runtime configuration is too large"
        }
        JSONObject(configJson)
        check(directory.exists() || directory.mkdirs()) { "Unable to create the VPN runtime directory" }
        val temporary = File(directory, "selected-config.tmp")
        temporary.outputStream().buffered().use { output ->
            output.write(configJson.toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }
        if (!temporary.renameTo(runtimeFile)) {
            temporary.copyTo(runtimeFile, overwrite = true)
            temporary.delete()
        }
    }

    fun read(): String {
        check(runtimeFile.isFile) { "No saved VPN configuration is available" }
        val bytes = runtimeFile.readBytes()
        check(bytes.size.toLong() <= MAX_CONFIG_BYTES) { "Saved VPN configuration is too large" }
        val text = bytes.toString(StandardCharsets.UTF_8)
        JSONObject(text)
        return text
    }

    fun isReady(): Boolean = runtimeFile.isFile && runtimeFile.length() in 2L..MAX_CONFIG_BYTES

    private companion object {
        const val MAX_CONFIG_BYTES = 4L * 1024L * 1024L
    }
}
