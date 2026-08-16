package org.quickping.app.data.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.quickping.app.model.GuardianCategory
import org.quickping.app.model.AppRelease
import org.quickping.app.model.NotificationItem
import org.quickping.app.model.ManagementInfo
import org.quickping.app.model.Server
import org.quickping.app.model.Service
import org.quickping.app.model.UserInfo

data class EmailChallenge(
    val id: String,
    val expiresInSeconds: Long,
    val debugCode: String? = null,
)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: UserInfo,
)

data class RefreshedSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

data class BootstrapPayload(
    val user: UserInfo,
    val services: List<Service>,
    val serversByService: Map<String, List<Server>>,
    val guardianCategories: List<GuardianCategory>,
    val notifications: List<NotificationItem>,
    val release: AppRelease?,
    val management: ManagementInfo = ManagementInfo(),
)

class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
) : IOException(message)

class QuickPingApiClient(baseUrl: String) {
    private val origin = baseUrl.trim().trimEnd('/')

    fun loginWithPassword(
        email: String,
        password: String,
        installationId: String,
        deviceName: String,
        appVersion: String,
    ): AuthSession {
        val data = request(
            method = "POST",
            path = "/api/v1/auth/login",
            body = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("installationId", installationId)
                .put("deviceName", deviceName)
                .put("appVersion", appVersion),
        )
        return AuthSession(
            accessToken = data.getString("accessToken"),
            refreshToken = data.getString("refreshToken"),
            expiresInSeconds = data.getLong("expiresInSeconds"),
            user = data.getJSONObject("user").toUserInfo(),
        )
    }

    fun requestEmailCode(email: String, installationId: String, language: String): EmailChallenge {
        val data = request(
            method = "POST",
            path = "/api/v1/auth/email/request",
            body = JSONObject()
                .put("email", email)
                .put("installationId", installationId)
                .put("language", language),
        )
        val challengeId = data.optNullableString("challengeId")
            ?: throw ApiException(202, "challenge_unavailable", "کد ورود برای این حساب در دسترس نیست")
        return EmailChallenge(
            id = challengeId,
            expiresInSeconds = data.optLong("expiresInSeconds", 600),
            debugCode = data.optNullableString("debugCode"),
        )
    }

    fun verifyEmailCode(
        challengeId: String,
        code: String,
        installationId: String,
        deviceName: String,
        appVersion: String,
    ): AuthSession {
        val data = request(
            method = "POST",
            path = "/api/v1/auth/email/verify",
            body = JSONObject()
                .put("challengeId", challengeId)
                .put("code", code)
                .put("installationId", installationId)
                .put("deviceName", deviceName)
                .put("appVersion", appVersion),
        )
        return AuthSession(
            accessToken = data.getString("accessToken"),
            refreshToken = data.getString("refreshToken"),
            expiresInSeconds = data.getLong("expiresInSeconds"),
            user = data.getJSONObject("user").toUserInfo(),
        )
    }

    fun refresh(refreshToken: String, installationId: String): RefreshedSession {
        val data = request(
            method = "POST",
            path = "/api/v1/auth/refresh",
            body = JSONObject()
                .put("refreshToken", refreshToken)
                .put("installationId", installationId),
        )
        return RefreshedSession(
            accessToken = data.getString("accessToken"),
            refreshToken = data.getString("refreshToken"),
            expiresInSeconds = data.getLong("expiresInSeconds"),
        )
    }

    fun bootstrap(accessToken: String): BootstrapPayload {
        val data = request("GET", "/api/v1/client/bootstrap", accessToken = accessToken)
        val servicesJson = data.optJSONArray("services") ?: JSONArray()
        val services = ArrayList<Service>(servicesJson.length())
        val serversByService = LinkedHashMap<String, List<Server>>()
        val guardianStates = LinkedHashMap<String, Boolean>()
        repeat(servicesJson.length()) { index ->
            val item = servicesJson.getJSONObject(index)
            val service = item.toService()
            services += service
            serversByService[service.id] = item.optJSONArray("servers").toServers()
            val guardian = item.optJSONObject("guardian")
            val rules = guardian?.optJSONArray("rules")
            if (rules != null) {
                repeat(rules.length()) { ruleIndex ->
                    val rule = rules.getJSONObject(ruleIndex)
                    guardianStates[rule.getString("category")] = rule.optBoolean("enabled")
                }
            }
        }
        return BootstrapPayload(
            user = data.getJSONObject("user").toUserInfo(),
            services = services,
            serversByService = serversByService,
            guardianCategories = guardianCatalog(guardianStates),
            notifications = data.optJSONArray("notifications").toNotifications(),
            release = data.optJSONObject("release")?.let { release ->
                AppRelease(
                    versionName = release.optString("versionName"),
                    versionCode = release.optInt("versionCode"),
                    minimumVersionCode = release.optInt("minimumVersionCode"),
                    mandatory = release.optBoolean("mandatory"),
                    changelog = release.optString("changelog"),
                    downloadUrl = release.optString("downloadUrl"),
                    sha256 = release.optString("sha256"),
                )
            },
            management = data.optJSONObject("management")?.let { management ->
                ManagementInfo(telegramUsername = management.optString("telegramUsername", "Folwn").normalizeTelegramUsername())
            } ?: ManagementInfo(),
        )
    }

    fun markNotificationsRead(accessToken: String, notificationIds: List<String>) {
        if (notificationIds.isEmpty()) return
        requestNoData(
            method = "PATCH",
            path = "/api/v1/client/notifications",
            body = JSONObject().put("notificationIds", JSONArray(notificationIds)),
            accessToken = accessToken,
        )
    }

    fun runtimeConfig(accessToken: String, serviceId: String, nodeId: String): String {
        val data = request(
            method = "GET",
            path = "/api/v1/client/services/${serviceId.urlPathSegment()}/config?nodeId=${nodeId.urlQueryValue()}",
            accessToken = accessToken,
        )
        val node = data.optJSONObject("node")
            ?: throw ApiException(200, "invalid_config", "پیکربندی سرور ناقص است")
        val runtimeConfig = node.opt("runtimeConfig")
        return when (runtimeConfig) {
            is JSONObject -> runtimeConfig.toString()
            is String -> runCatching { JSONObject(runtimeConfig).toString() }.getOrElse {
                throw ApiException(200, "invalid_config", "پیکربندی سرور معتبر نیست")
            }
            else -> throw ApiException(200, "invalid_config", "پیکربندی سرور موجود نیست")
        }
    }

    fun requestPasswordChange(accessToken: String): EmailChallenge {
        val data = request(
            method = "POST",
            path = "/api/v1/client/account/password/request",
            body = JSONObject(),
            accessToken = accessToken,
        )
        return EmailChallenge(
            id = data.getString("challengeId"),
            expiresInSeconds = data.optLong("expiresInSeconds", 600),
            debugCode = data.optNullableString("debugCode"),
        )
    }

    fun confirmPasswordChange(
        accessToken: String,
        challengeId: String,
        code: String,
        newPassword: String,
    ) {
        requestNoData(
            method = "POST",
            path = "/api/v1/client/account/password/confirm",
            body = JSONObject()
                .put("challengeId", challengeId)
                .put("code", code)
                .put("newPassword", newPassword),
            accessToken = accessToken,
        )
    }

    fun changePassword(accessToken: String, currentPassword: String, newPassword: String) {
        requestNoData(
            method = "POST",
            path = "/api/v1/client/account/password/change",
            body = JSONObject()
                .put("currentPassword", currentPassword)
                .put("newPassword", newPassword),
            accessToken = accessToken,
        )
    }

    fun deleteAccount(accessToken: String, password: String) {
        requestNoData(
            method = "DELETE",
            path = "/api/v1/client/account",
            body = JSONObject().put("password", password),
            accessToken = accessToken,
        )
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        accessToken: String? = null,
    ): JSONObject {
        val response = execute(method, path, body, accessToken)
        val envelope = parseEnvelope(response.status, response.text)
        if (response.status !in 200..299) throwApiError(response.status, envelope)
        return envelope.optJSONObject("data")
            ?: throw ApiException(response.status, "invalid_response", "دادهٔ پاسخ سرور ناقص است")
    }

    private fun requestNoData(
        method: String,
        path: String,
        body: JSONObject? = null,
        accessToken: String? = null,
    ) {
        val response = execute(method, path, body, accessToken)
        if (response.status !in 200..299) {
            val envelope = response.text.takeIf { it.isNotBlank() }?.let { text ->
                runCatching { JSONObject(text) }.getOrNull()
            }
            if (envelope != null) throwApiError(response.status, envelope)
            throw ApiException(
                response.status,
                "http_${response.status}",
                "سرور درخواست را با خطای HTTP ${response.status} رد کرد",
            )
        }
        if (response.text.isBlank()) return
        runCatching { JSONObject(response.text) }.getOrElse {
            throw ApiException(response.status, "invalid_response", "پاسخ موفق سرور ساختار معتبری ندارد")
        }
    }

    private fun execute(
        method: String,
        path: String,
        body: JSONObject?,
        accessToken: String?,
    ): RawResponse {
        val endpoint = URL(origin)
        require(endpoint.protocol == "https" || (endpoint.protocol == "http" && endpoint.host == "10.0.2.2")) {
            "QuickPing API must use HTTPS outside the Android emulator"
        }
        val connection = (URL(origin + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("accept", "application/json")
            setRequestProperty("user-agent", "QuickPing-Android/2.6")
            if (accessToken != null) setRequestProperty("authorization", "Bearer $accessToken")
            if (body != null) {
                doOutput = true
                setRequestProperty("content-type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            return RawResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEnvelope(status: Int, text: String): JSONObject =
        runCatching { JSONObject(text) }.getOrElse {
            throw ApiException(status, "invalid_response", "پاسخ سرور قابل خواندن نیست")
        }

    private fun throwApiError(status: Int, envelope: JSONObject): Nothing {
        val error = envelope.optJSONObject("error")
        throw ApiException(
            status = status,
            code = error?.optString("code").orEmpty().ifBlank { "request_failed" },
            message = error?.optString("message").orEmpty().ifBlank { "درخواست انجام نشد" },
        )
    }

    private data class RawResponse(val status: Int, val text: String)
}

private fun JSONObject.toUserInfo() = UserInfo(
    id = getString("id"),
    email = getString("email"),
    emailVerified = optBoolean("emailVerified"),
    telegramBound = optBoolean("telegramBound"),
    balance = optLongFlexible("balance"),
    language = optString("language", "fa"),
    status = optString("status", "ACTIVE"),
)

private fun JSONObject.toService(): Service {
    val expiresAt = optNullableString("expiryTime")
    val expiresAtMillis = expiresAt?.let(::parseIsoInstant) ?: 0L
    val daysLeft = TimeUnit.MILLISECONDS.toDays((expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0))
    return Service(
        id = getString("id"),
        name = optString("name", "سرویس شخصی"),
        plan = optString("plan", "NimHUB"),
        license = optString("license"),
        totalBytes = optLongFlexible("size"),
        usedBytes = optLongFlexible("usedSize"),
        daysLeft = daysLeft.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        usersCount = optInt("usersCount", 1),
        isFree = optBoolean("isFree"),
        providerState = optString("providerState", "READY"),
    )
}

private fun JSONArray?.toServers(): List<Server> {
    if (this == null) return emptyList()
    return List(length()) { index ->
        val item = getJSONObject(index)
        val country = item.optString("countryCode", "global").lowercase()
        val location = item.optString("location", country.uppercase())
        Server(
            id = item.getString("id"),
            countryCode = country,
            countryName = location,
            title = item.optString("remarks", location),
            remarks = item.optString("remarks"),
            host = item.optString("host"),
            port = item.optInt("port"),
            coreType = item.optString("coreType", "sing-box"),
            freeAllowed = item.optBoolean("freeAllowed"),
            unmetered = item.optBoolean("unmetered"),
        )
    }
}

private fun JSONArray?.toNotifications(): List<NotificationItem> {
    if (this == null) return emptyList()
    return List(length()) { index ->
        val item = getJSONObject(index)
        NotificationItem(
            id = item.getString("id"),
            title = item.optString("title"),
            body = item.optString("body"),
            createdAt = item.optString("publishedAt", item.optString("createdAt")),
            read = item.optBoolean("read", false),
            category = item.optString("category", "SYSTEM"),
        )
    }
}

private fun String.normalizeTelegramUsername(): String =
    trim().removePrefix("@").takeIf { it.matches(Regex("[A-Za-z0-9_]{5,32}")) } ?: "Folwn"

private fun guardianCatalog(values: Map<String, Boolean>) = listOf(
    GuardianCategory("malware", "بدافزارها", "محافظت در برابر دامنه‌های مخرب", "malware", values["malware"] ?: true),
    GuardianCategory("ads", "تبلیغات و ردیاب‌ها", "مسدودکردن تبلیغات و ابزارهای ردیابی", "ads", values["ads"] ?: true),
    GuardianCategory("youtube", "تبلیغات یوتیوب", "کاهش تبلیغات و دامنه‌های مزاحم", "youtube", values["youtube"] ?: false),
    GuardianCategory("phishing", "فیشینگ", "جلوگیری از صفحات جعل هویت", "phishing", values["phishing"] ?: true),
    GuardianCategory("porn", "محتوای بزرگسال", "فیلتر دامنه‌های نامناسب", "porn", values["porn"] ?: false),
    GuardianCategory("government", "وب‌سایت‌های دولتی", "کنترل دسترسی به دامنه‌های دولتی", "government", values["government"] ?: false),
    GuardianCategory("payment", "درگاه‌های پرداخت", "کنترل دسترسی به درگاه‌های مالی", "payment", values["payment"] ?: false),
    GuardianCategory("socials", "شبکه‌های اجتماعی", "مدیریت دسترسی به شبکه‌های اجتماعی", "socials", values["socials"] ?: false),
    GuardianCategory("crypto", "رمزارز", "کنترل سایت‌ها و سرویس‌های رمزارز", "crypto", values["crypto"] ?: false),
    GuardianCategory("fake-news", "اخبار جعلی", "فیلتر منابع شناخته‌شدهٔ گمراه‌کننده", "fake_news", values["fake-news"] ?: false),
)

private fun JSONObject.optLongFlexible(name: String): Long = when (val value = opt(name)) {
    is Number -> value.toLong()
    is String -> value.toLongOrNull() ?: 0L
    else -> 0L
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun parseIsoInstant(value: String): Long {
    val normalized = value.replace(Regex("(\\.\\d{3})\\d+Z$"), "\$1Z")
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
    )
    return formats.firstNotNullOfOrNull { pattern ->
        runCatching {
            java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(normalized)?.time
        }.getOrNull()
    } ?: 0L
}

private fun String.urlPathSegment(): String = java.net.URLEncoder.encode(this, "UTF-8")

private fun String.urlQueryValue(): String = java.net.URLEncoder.encode(this, "UTF-8")
