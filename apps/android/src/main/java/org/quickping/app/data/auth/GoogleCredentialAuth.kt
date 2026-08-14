package org.quickping.app.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.quickping.app.data.network.ApiException
import org.quickping.app.data.network.GoogleNonceChallenge

class GoogleCredentialAuth(private val activity: Activity) {
    suspend fun getIdToken(challenge: GoogleNonceChallenge): String {
        try {
            val option = GetSignInWithGoogleOption.Builder(challenge.serverClientId)
                .setNonce(challenge.nonce)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val credential = CredentialManager.create(activity)
                .getCredential(activity, request)
                .credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw ApiException(
                    status = 0,
                    code = "google_credential_type",
                    message = "حساب گوگل انتخاب شد اما توکن ورود معتبر دریافت نشد",
                )
            }
            return runCatching { GoogleIdTokenCredential.createFrom(credential.data).idToken }
                .getOrElse {
                    throw ApiException(
                        status = 0,
                        code = "google_token_parse",
                        message = "توکن ورود گوگل قابل خواندن نبود؛ تنظیمات OAuth برنامه را بررسی کنید",
                    )
                }
        } catch (error: GetCredentialCancellationException) {
            throw ApiException(
                status = 0,
                code = "google_cancelled",
                message = "ورود گوگل کامل نشد؛ اگر حساب را انتخاب کردید، SHA-1 و Android OAuth را بررسی کنید",
            )
        } catch (error: GetCredentialException) {
            throw ApiException(
                status = 0,
                code = "google_credential_failed",
                message = "Google Play Services ورود را نپذیرفت؛ Android OAuth و امضای APK را بررسی کنید",
            )
        }
    }
}
