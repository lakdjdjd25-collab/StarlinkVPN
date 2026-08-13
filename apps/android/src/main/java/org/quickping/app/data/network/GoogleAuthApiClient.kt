package org.quickping.app.data.network

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.quickping.app.model.UserInfo

data class GoogleNonceChallenge(val id: String, val nonce: String, val serverClientId: String)

class GoogleAuthApiClient(baseUrl: String) {
    private val origin = baseUrl.trim().trimEnd('/')

    fun requestNonce(installationId: String): GoogleNonceChallenge {
        val data = post("/api/v1/auth/google/nonce", JSONObject().put("installationId", installationId))
        return GoogleNonceChallenge(data.getString("challengeId"), data.getString("nonce"), data.getString("serverClientId"))
    }

    fun login(challenge: GoogleNonceChallenge, idToken: String, installationId: String, deviceName: String, appVersion: String, language: String): AuthSession {
        val data = post("/api/v1/auth/google", JSONObject()
            .put("challengeId", challenge.id).put("nonce", challenge.nonce).put("idToken", idToken)
            .put("installationId", installationId).put("deviceName", deviceName).put("appVersion", appVersion).put("language", language))
        val user = data.getJSONObject("user")
        return AuthSession(data.getString("accessToken"), data.getString("refreshToken"), data.getLong("expiresInSeconds"), UserInfo(
            id = user.getString("id"), email = user.getString("email"), emailVerified = user.optBoolean("emailVerified"),
            telegramBound = user.optBoolean("telegramBound"), balance = user.optString("balance", "0").toLongOrNull() ?: 0L,
            language = user.optString("language", "fa"),
        ))
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val base = URL(origin)
        require(base.protocol == "https" || (base.protocol == "http" && base.host == "10.0.2.2"))
        val connection = URL(origin + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 12_000; connection.readTimeout = 30_000
            connection.doOutput = true; connection.useCaches = false
            connection.setRequestProperty("accept", "application/json")
            connection.setRequestProperty("content-type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            val envelope = runCatching { JSONObject(text) }.getOrElse { throw ApiException(status, "invalid_response", "پاسخ سرور معتبر نیست") }
            if (status !in 200..299) {
                val error = envelope.optJSONObject("error")
                throw ApiException(status, error?.optString("code").orEmpty().ifBlank { "request_failed" }, error?.optString("message").orEmpty().ifBlank { "درخواست انجام نشد" })
            }
            return envelope.optJSONObject("data") ?: throw ApiException(status, "invalid_response", "داده پاسخ ناقص است")
        } finally { connection.disconnect() }
    }
}
