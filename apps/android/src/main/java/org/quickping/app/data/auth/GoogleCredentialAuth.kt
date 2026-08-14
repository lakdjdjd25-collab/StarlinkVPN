package org.quickping.app.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.quickping.app.data.network.ApiException
import org.quickping.app.data.network.GoogleNonceChallenge

class GoogleCredentialAuth(private val activity: Activity) {
    private val credentialManager by lazy { CredentialManager.create(activity) }

    suspend fun getIdToken(challenge: GoogleNonceChallenge): String {
        val directAttempt = runCatching { requestDirectGoogleSignIn(challenge) }
        directAttempt.getOrNull()?.let { return it }

        // Some Google Play Services / Credential Manager combinations return a
        // cancellation after an account was picked when the account has not yet
        // authorized this app. Retry once with the general Google account picker
        // and explicitly include non-authorized accounts.
        val pickerAttempt = runCatching { requestGoogleAccountPicker(challenge) }
        pickerAttempt.getOrNull()?.let { return it }

        val error = pickerAttempt.exceptionOrNull() ?: directAttempt.exceptionOrNull()
        if (error is ApiException) throw error
        if (error is GetCredentialException) {
            throw ApiException(
                status = 0,
                code = "google_credential_failed",
                message = "ورود گوگل توسط Credential Manager رد شد (${error.javaClass.simpleName})؛ Android OAuth و امضای همین نسخه را بررسی کنید",
            )
        }
        throw ApiException(
            status = 0,
            code = "google_credential_failed",
            message = "ورود گوگل کامل نشد (${error?.javaClass?.simpleName ?: "unknown"})",
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
                message = "حساب گوگل انتخاب شد اما Google ID Token معتبر دریافت نشد",
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
