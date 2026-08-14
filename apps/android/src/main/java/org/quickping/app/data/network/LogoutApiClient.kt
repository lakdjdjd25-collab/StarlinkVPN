package org.quickping.app.data.network

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

internal class LogoutApiClient(baseUrl: String) {
    private val origin = baseUrl.trim().trimEnd('/')

    fun revoke(refreshToken: String, installationId: String) {
        if (refreshToken.isBlank() || installationId.isBlank()) return
        val base = URL(origin)
        require(base.protocol == "https" || (base.protocol == "http" && base.host == "10.0.2.2")) {
            "nimHUB API must use HTTPS outside the Android emulator"
        }
        val connection = (URL("$origin/api/v1/auth/logout").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 7_000
            useCaches = false
            doOutput = true
            setRequestProperty("accept", "application/json")
            setRequestProperty("content-type", "application/json; charset=utf-8")
            setRequestProperty("user-agent", "nimHUB-Android/2.6")
        }
        try {
            val body = JSONObject()
                .put("refreshToken", refreshToken)
                .put("installationId", installationId)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { it.write(body) }
            // Sign-out is best effort from the client. Reading the response makes
            // sure the request is actually sent before the connection is closed.
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            stream?.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
