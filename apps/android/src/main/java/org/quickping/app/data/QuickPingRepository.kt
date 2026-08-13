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
import org.quickping.app.data.network.QuickPingApiClient
import org.quickping.app.data.security.SecureTokenStore
import org.quickping.app.data.security.StoredSession

class QuickPingRepository(context: Context) {
    private val api = QuickPingApiClient(BuildConfig.API_BASE_URL)
    private val tokens = SecureTokenStore(context.applicationContext)
    private val refreshMutex = Mutex()

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
        val accessToken = validAccessToken()
        try {
            api.runtimeConfig(accessToken, serviceId, nodeId)
        } catch (error: ApiException) {
            if (error.status != 401) throw error
            api.runtimeConfig(refreshAccessToken(force = true), serviceId, nodeId)
        }
    }

    fun signOut() = tokens.clear()

    private fun saveAndBootstrap(session: org.quickping.app.data.network.AuthSession): BootstrapPayload {
        tokens.save(
            StoredSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                accessExpiresAtMillis = expiryFromNow(session.expiresInSeconds),
            ),
        )
        return try {
            api.bootstrap(session.accessToken)
        } catch (error: Throwable) {
            tokens.clear()
            throw error
        }
    }

    private suspend fun bootstrapAuthenticated(): BootstrapPayload {
        val accessToken = validAccessToken()
        return try {
            api.bootstrap(accessToken)
        } catch (error: ApiException) {
            if (error.status != 401) throw error
            api.bootstrap(refreshAccessToken(force = true))
        }
    }

    private suspend fun validAccessToken(): String {
        val session = tokens.load() ?: throw ApiException(401, "signed_out", "ورود لازم است")
        if (session.accessExpiresAtMillis > System.currentTimeMillis() + 30_000) {
            return session.accessToken
        }
        return refreshAccessToken(force = false)
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
}
