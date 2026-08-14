package org.quickping.app.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.quickping.app.data.network.ApiException
import org.quickping.app.data.network.GoogleNonceChallenge

class GoogleCredentialAuth(private val activity: Activity) {
    private val credentialManager by lazy { CredentialManager.create(activity) }

    suspend fun getIdToken(challenge: GoogleNonceChallenge): String {
        validateChallenge(challenge)

        // Explicit Google button flow first. A genuine user cancellation must be
        // propagated immediately; opening a second picker after the user pressed
        // Back is both misleading and can look like an authentication loop.
        val directAttempt = tryCredentialAttempt {
            delay(250)
            requestDirectGoogleSignIn(challenge)
        }
        directAttempt.token?.let { return it }
        directAttempt.error.throwIfCancellation()

        // Official Credential Manager account-picker fallback. This includes
        // accounts that have never authorized nimHUB and covers devices where the
        // explicit Sign in with Google option cannot surface a usable credential.
        val pickerAttempt = tryCredentialAttempt {
            delay(300)
            requestGoogleAccountPicker(challenge)
        }
        pickerAttempt.token?.let { return it }
        pickerAttempt.error.throwIfCancellation()

        val directError = directAttempt.error
        val pickerError = pickerAttempt.error
        val preferredError = pickerError ?: directError
        if (preferredError is ApiException) throw preferredError

        val signingIdentity = signingIdentityDiagnostic()
        if (directError is NoCredentialException && pickerError is NoCredentialException) {
            throw ApiException(
                status = 0,
                code = "google_no_credential",
                message = "هیچ حساب Google قابل استفاده‌ای برای این نسخه پیدا نشد. Android OAuth باید دقیقاً با $signingIdentity ثبت شده باشد.",
            )
        }

        val credentialErrors = listOfNotNull(directError, pickerError)
            .filterIsInstance<GetCredentialException>()
        val diagnostic = credentialErrors
            .map { it.javaClass.simpleName }
            .distinct()
            .joinToString("/")
            .ifBlank { preferredError?.javaClass?.simpleName ?: "unknown" }

        when {
            credentialErrors.any { it.javaClass.simpleName.contains("ProviderConfiguration", ignoreCase = true) } -> {
                throw ApiException(
                    status = 0,
                    code = "google_provider_config",
                    message = "Google Credential Provider تنظیمات این نسخه را نپذیرفت ($diagnostic). Android OAuth باید دقیقاً با $signingIdentity ثبت شده باشد.",
                )
            }
            credentialErrors.any { it.javaClass.simpleName.contains("Unsupported", ignoreCase = true) } -> {
                throw ApiException(
                    status = 0,
                    code = "google_unsupported",
                    message = "ورود Google روی Credential Provider این دستگاه پشتیبانی نمی‌شود ($diagnostic). Google Play services و سیستم را به‌روز کنید.",
                )
            }
            credentialErrors.isNotEmpty() -> {
                throw ApiException(
                    status = 0,
                    code = "google_credential_failed",
                    message = "ورود Google توسط Credential Manager کامل نشد ($diagnostic)",
                )
            }
            else -> throw ApiException(
                status = 0,
                code = "google_credential_failed",
                message = "ورود Google کامل نشد ($diagnostic)",
            )
        }
    }

    private suspend fun tryCredentialAttempt(block: suspend () -> String): CredentialAttempt = try {
        CredentialAttempt(token = block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        CredentialAttempt(error = error)
    }

    private fun Throwable?.throwIfCancellation() {
        if (this is GetCredentialCancellationException) throw this
    }

    private fun validateChallenge(challenge: GoogleNonceChallenge) {
        val clientId = challenge.serverClientId.trim()
        if (
            clientId.length !in 20..512 ||
            !clientId.endsWith(".apps.googleusercontent.com", ignoreCase = true) ||
            challenge.nonce.length !in 32..256
        ) {
            throw ApiException(
                status = 0,
                code = "google_oauth_config",
                message = "تنظیمات OAuth دریافت‌شده از سرور معتبر نیست",
            )
        }
    }

    private fun signingIdentityDiagnostic(): String =
        currentAppSigningIdentity(activity)?.let { identity ->
            "package=${identity.packageName} و SHA-1=${identity.sha1}"
        } ?: "package=${activity.packageName} و SHA-1 امضای همین APK"

    private suspend fun requestDirectGoogleSignIn(challenge: GoogleNonceChallenge): String {
        val option = GetSignInWithGoogleOption.Builder(challenge.serverClientId)
            .setNonce(challenge.nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        return credentialManager.getCredential(activity, request).credential.toIdToken()
    }

    private suspend fun requestGoogleAccountPicker(challenge: GoogleNonceChallenge): String {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(challenge.serverClientId)
            .setNonce(challenge.nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        return credentialManager.getCredential(activity, request).credential.toIdToken()
    }

    private fun androidx.credentials.Credential.toIdToken(): String {
        if (this !is CustomCredential || type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw ApiException(
                status = 0,
                code = "google_credential_type",
                message = "حساب Google انتخاب شد اما Google ID Token معتبر دریافت نشد",
            )
        }
        return runCatching { GoogleIdTokenCredential.createFrom(data).idToken }
            .getOrElse {
                throw ApiException(
                    status = 0,
                    code = "google_token_parse",
                    message = "Google ID Token قابل خواندن نبود؛ تنظیمات OAuth برنامه را بررسی کنید",
                )
            }
    }

    private data class CredentialAttempt(
        val token: String? = null,
        val error: Throwable? = null,
    )
}
