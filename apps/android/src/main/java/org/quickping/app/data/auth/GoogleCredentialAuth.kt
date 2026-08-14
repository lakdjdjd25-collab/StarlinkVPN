package org.quickping.app.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.delay
import org.quickping.app.data.network.ApiException
import org.quickping.app.data.network.GoogleNonceChallenge

class GoogleCredentialAuth(private val activity: Activity) {
    private val credentialManager by lazy { CredentialManager.create(activity) }

    suspend fun getIdToken(challenge: GoogleNonceChallenge): String {
        // Explicit button flow first. This remains the preferred path for the
        // Google button and, unlike the bottom-sheet flow, is not affected by
        // the account-level "Sign in prompts" preference.
        val directAttempt = runCatching {
            delay(250)
            requestDirectGoogleSignIn(challenge)
        }
        directAttempt.getOrNull()?.let { return it }

        // Official Credential Manager fallback for NoCredentialException:
        // request every Google account on the device, including accounts that
        // have never authorized this application. A small delay avoids racing
        // the provider immediately after the explicit button flow is dismissed.
        val pickerAttempt = runCatching {
            delay(300)
            requestGoogleAccountPicker(challenge)
        }
        pickerAttempt.getOrNull()?.let { return it }

        val directError = directAttempt.exceptionOrNull()
        val pickerError = pickerAttempt.exceptionOrNull()
        val error = pickerError ?: directError
        if (error is ApiException) throw error

        if (directError is NoCredentialException && pickerError is NoCredentialException) {
            throw ApiException(
                status = 0,
                code = "google_no_credential",
                message = "هیچ حساب Google قابل استفاده‌ای برای این امضای برنامه پیدا نشد. حساب Google دستگاه، Android OAuth و SHA-1 امضای همین نسخه باید با هم تطابق داشته باشند.",
            )
        }
        if (error is GetCredentialException) {
            throw ApiException(
                status = 0,
                code = "google_credential_failed",
                message = "ورود Google توسط Credential Manager کامل نشد (${error.javaClass.simpleName})",
            )
        }
        throw ApiException(
            status = 0,
            code = "google_credential_failed",
            message = "ورود Google کامل نشد (${error?.javaClass?.simpleName ?: "unknown"})",
        )
    }

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
}
