package org.quickping.app.data

import android.content.Context
import android.os.Build
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.quickping.app.BuildConfig
import org.quickping.app.data.network.ApiException
import org.quickping.app.data.network.BootstrapPayload
import org.quickping.app.data.network.EmailChallenge
import org.quickping.app.data.network.GoogleAuthApiClient
import org.quickping.app.data.network.GoogleNonceChallenge
import org.quickping.app.data.network.ManualServerApiClient
import org.quickping.app.data.network.ManualTrafficResult
import org.quickping.app.data.network.ManualTrafficSession
import org.quickping.app.data.network.QuickPingApiClient
import org.quickping.app.data.security.SecureTokenStore
import org.quickping.app.data.security.StoredSession
import org.quickping.app.data.traffic.ManualTrafficRuntimeRegistry
import org.quickping.app.data.traffic.ManualTrafficStore
import org.quickping.app.data.traffic.PendingManualTrafficSession

class QuickPingRepository(context: Context) {
    private val api = QuickPingApiClient(BuildConfig.API_BASE_URL)
    private val manualApi = ManualServerApiClient(BuildConfig.API_BASE_URL)
    private val googleApi = GoogleAuthApiClient(BuildConfig.API_BASE_URL)
    private val tokens = SecureTokenStore(context.applicationContext)
    private val manualTrafficStore = ManualTrafficStore(context.applicationContext)
    private val refreshMutex = Mutex()

    @Volatile
    private var manualServersByService: Map<String, Set<String>> = emptyMap()

    suspend fun restoreSession(): BootstrapPayload? = withContext(Dispatchers.IO) {
        if (tokens.load() == null) return@withContext null
        runCatching { bootstrapAuthenticated() }.getOrElse {
            if (it is ApiException && it.status == 401) tokens.clear()
            null
        }
    }

    suspend fun requestEmailCode(email: String, language: String): EmailChallenge =
        withContext(Dispatchers.IO) {
            api.requestEmailCode(email, tokens.installationId(), language)
        }

    suspend fun requestGoogleNonce(): GoogleNonceChallenge = withContext(Dispatchers.IO) {
        googleApi.requestNonce(tokens.installationId())
    }

    suspend fun loginWithGoogle(
        challenge: GoogleNonceChallenge,
        idToken: String,
        language: String,
    ): BootstrapPayload = withContext(Dispatchers.IO) {
        val session = googleApi.login(
            challenge = challenge,
            idToken = idToken,
            installationId = tokens.installationId(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            appVersion = BuildConfig.VERSION_NAME,
            language = language,
        )
        saveAndBootstrap(session)
    }

    suspend fun loginWithPassword(email: String, password: String): BootstrapPayload =
        withContext(Dispatchers.IO) {
            val session = api.loginWithPassword(
                email = email,
                password = password,
                installationId = tokens.installationId(),
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                appVersion = BuildConfig.VERSION_NAME,
            )
            saveAndBootstrap(session)
        }

    suspend fun verifyEmailCode(challengeId: String, code: String): BootstrapPayload =
        withContext(Dispatchers.IO) {
            val session = api.verifyEmailCode(
                challengeId = challengeId,
                code = code,
                installationId = tokens.installationId(),
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                appVersion = BuildConfig.VERSION_NAME,
            )
            saveAndBootstrap(session)
        }

    suspend fun runtimeConfig(serviceId: String, nodeId: String): String = withContext(Dispatchers.IO) {
        recoverStoredManualTraffic()
        ManualTrafficRuntimeRegistry.clear()
        val manual = manualServersByService[serviceId]?.contains(nodeId) == true
        val config = authenticatedRequest { accessToken ->
            if (manual) {
                manualApi.runtimeConfig(accessToken, serviceId, nodeId)
            } else {
                api.runtimeConfig(accessToken, serviceId, nodeId)
            }
        }
        if (manual) {
            val traffic = authenticatedRequest { accessToken ->
                api.startManualTraffic(accessToken, serviceId, nodeId)
            }
            ManualTrafficRuntimeRegistry.replace(
                PendingManualTrafficSession(
                    sessionId = traffic.sessionId,
                    serviceId = traffic.serviceId,
                    serverId = traffic.serverId,
                    remainingBytes = traffic.remainingBytes,
                    countTraffic = traffic.countTraffic,
                ),
            )
        }
        config
    }

    suspend fun startManualTraffic(serviceId: String, serverId: String): ManualTrafficSession =
        withContext(Dispatchers.IO) {
            authenticatedRequest { accessToken ->
                api.startManualTraffic(accessToken, serviceId, serverId)
            }
        }

    suspend fun reportManualTraffic(
        sessionId: String,
        uploadedBytes: Long,
        downloadedBytes: Long,
        finalize: Boolean = false,
    ): ManualTrafficResult = withContext(Dispatchers.IO) {
        authenticatedRequest { accessToken ->
            api.reportManualTraffic(
                accessToken = accessToken,
                sessionId = sessionId,
                uploadedBytes = uploadedBytes,
                downloadedBytes = downloadedBytes,
                finalize = finalize,
            )
        }
    }

    suspend fun requestPasswordChange(): EmailChallenge = withContext(Dispatchers.IO) {
        authenticatedRequest(api::requestPasswordChange)
    }

    suspend fun confirmPasswordChange(challengeId: String, code: String, newPassword: String) =
        withContext(Dispatchers.IO) {
            authenticatedRequest { accessToken ->
                api.confirmPasswordChange(accessToken, challengeId, code, newPassword)
            }
        }

    suspend fun changePassword(currentPassword: String, newPassword: String) = withContext(Dispatchers.IO) {
        authenticatedRequest { accessToken ->
            api.changePassword(accessToken, currentPassword, newPassword)
        }
    }

    suspend fun deleteAccount(password: String) = withContext(Dispatchers.IO) {
        authenticatedRequest { accessToken -> api.deleteAccount(accessToken, password) }
    }

    suspend fun markNotificationsRead(notificationIds: List<String>) = withContext(Dispatchers.IO) {
        if (notificationIds.isNotEmpty()) {
            authenticatedRequest { accessToken -> api.markNotificationsRead(accessToken, notificationIds) }
        }
    }

    fun signOut() {
        ManualTrafficRuntimeRegistry.clear()
        manualServersByService = emptyMap()
        tokens.clear()
    }

    private suspend fun recoverStoredManualTraffic() {
        val stale = manualTrafficStore.load() ?: return
        try {
            val result = authenticatedRequest { accessToken ->
                api.reportManualTraffic(
                    accessToken = accessToken,
                    sessionId = stale.sessionId,
                    uploadedBytes = stale.uploadedBytes,
                    downloadedBytes = stale.downloadedBytes,
                    finalize = true,
                )
            }
            manualTrafficStore.confirm(result.remainingBytes, result.totalBytes)
            manualTrafficStore.clear()
        } catch (error: ApiException) {
            if (error.code in CLOSED_TRAFFIC_ERRORS) {
                manualTrafficStore.clear()
                return
            }
            throw error
        }
    }

    private fun saveAndBootstrap(session: org.quickping.app.data.network.AuthSession): BootstrapPayload {
        tokens.save(
            StoredSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                accessExpiresAtMillis = expiryFromNow(session.expiresInSeconds),
            ),
        )
        return try {
            api.bootstrap(session.accessToken).also(::rememberServerKinds)
        } catch (error: Throwable) {
            tokens.clear()
            throw error
        }
    }

    private suspend fun bootstrapAuthenticated(): BootstrapPayload {
        val payload = authenticatedRequest(api::bootstrap)
        rememberServerKinds(payload)
        return payload
    }

    private fun rememberServerKinds(payload: BootstrapPayload) {
        manualServersByService = payload.serversByService.mapValues { (_, servers) ->
            servers.asSequence().filter { it.isManual }.map { it.id }.toSet()
        }
    }

    private suspend fun validAccessToken(): String {
        val session = tokens.load() ?: throw ApiException(401, "signed_out", "ورود لازم است")
        if (session.accessExpiresAtMillis > System.currentTimeMillis() + 30_000) {
            return session.accessToken
        }
        return refreshAccessToken(force = false)
    }

    private suspend fun <T> authenticatedRequest(block: (String) -> T): T {
        val accessToken = validAccessToken()
        return try {
            block(accessToken)
        } catch (error: ApiException) {
            if (error.status != 401 || error.code !in AUTH_TOKEN_ERRORS) throw error
            block(refreshAccessToken(force = true))
        }
    }

    private suspend fun refreshAccessToken(force: Boolean): String = refreshMutex.withLock {
        val current = tokens.load() ?: throw ApiException(401, "signed_out", "ورود لازم است")
        if (!force && current.accessExpiresAtMillis > System.currentTimeMillis() + 30_000) {
            return@withLock current.accessToken
        }
        val refreshed = api.refresh(current.refreshToken, tokens.installationId())
        tokens.save(
            StoredSession(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken,
                accessExpiresAtMillis = expiryFromNow(refreshed.expiresInSeconds),
            ),
        )
        refreshed.accessToken
    }

    private fun expiryFromNow(seconds: Long): Long =
        System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds.coerceAtLeast(60))

    private companion object {
        val AUTH_TOKEN_ERRORS = setOf("missing_token", "invalid_token")
        val CLOSED_TRAFFIC_ERRORS = setOf("traffic_session_closed", "traffic_session_not_found")
    }
}
