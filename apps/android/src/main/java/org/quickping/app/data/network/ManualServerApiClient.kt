package org.quickping.app.data.network

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

internal class ManualServerApiClient(baseUrl: String) {
    private val origin = baseUrl.trim().trimEnd('/')

    fun runtimeConfig(accessToken: String, serviceId: String, nodeId: String): String {
        val endpoint = URL(origin)
        require(endpoint.protocol == "https" || (endpoint.protocol == "http" && endpoint.host == "10.0.2.2")) {
            "QuickPing API must use HTTPS outside the Android emulator"
        }
        val path = "/api/v1/client/services/${serviceId.urlPathSegmentManual()}/config?nodeId=${nodeId.urlQueryManual()}"
        val connection = (URL(origin + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("accept", "application/json")
            setRequestProperty("user-agent", "QuickPing-Android/2.6.19")
            setRequestProperty("authorization", "Bearer $accessToken")
            setRequestProperty(MANUAL_TRAFFIC_CAPABILITY_HEADER, "1")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            val envelope = runCatching { JSONObject(text) }.getOrElse {
                throw ApiException(status, "invalid_response", "پاسخ سرور قابل خواندن نیست")
            }
            if (status !in 200..299) {
                val error = envelope.optJSONObject("error")
                throw ApiException(
                    status = status,
                    code = error?.optString("code").orEmpty().ifBlank { "request_failed" },
                    message = error?.optString("message").orEmpty().ifBlank { "درخواست انجام نشد" },
                )
            }
            val node = envelope.optJSONObject("data")?.optJSONObject("node")
                ?: throw ApiException(status, "invalid_config", "پیکربندی سرور ناقص است")
            val runtimeConfig = node.opt("runtimeConfig")
            return when (runtimeConfig) {
                is JSONObject -> runtimeConfig.toString()
                is String -> runCatching { JSONObject(runtimeConfig).toString() }.getOrElse {
                    throw ApiException(status, "invalid_config", "پیکربندی سرور معتبر نیست")
                }
                else -> throw ApiException(status, "invalid_config", "پیکربندی سرور موجود نیست")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MANUAL_TRAFFIC_CAPABILITY_HEADER = "x-nimhub-manual-traffic"
    }
}

private fun String.urlPathSegmentManual(): String = java.net.URLEncoder.encode(this, "UTF-8")
private fun String.urlQueryManual(): String = java.net.URLEncoder.encode(this, "UTF-8")
