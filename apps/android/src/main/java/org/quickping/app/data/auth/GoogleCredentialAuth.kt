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
import org.quickping.app.data.network.ApiException
import org.quickping.app.data.network.GoogleNonceChallenge

class GoogleCredentialAuth(private val activity: Activity) {
    private val credentialManager by lazy { CredentialManager.create(activity) }

    suspend fun getIdToken(challenge: GoogleNonceChallenge): String {
        // Prefer Google's explicit button flow because the user tapped the
        // Google button. Some provider/device combinations reject that option
        // even though the regular Google account picker is available, so any
        // non-cancellation credential failure falls back to the picker.
        var directFailure: GetCredentialException? = null
        try {
            return requestDirectGoogleSignIn(challenge)
        } catch (_: GetCredentialCancellationException) {
            throw cancelled()
        } catch (error: GetCredentialException) {
            directFailure = error
        }

        try {
            return requestGoogleAccountPicker(challenge)
        } catch (_: GetCredentialCancellationException) {
            throw cancelled()
        } catch (error: NoCredentialException) {
            if (directFailure != null) throw credentialFailure(error, directFailure)
            throw ApiException(
                status = 0,
                code = "google_no_account",
                message = "No available Google account was found on this device",
            )
        } catch (error: GetCredentialException) {
            throw credentialFailure(error, directFailure)
        }
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

    private fun cancelled() = ApiException(
        status = 0,
        code = "google_cancelled",
        message = "Google sign-in was cancelled",
    )

    private fun credentialFailure(
        error: GetCredentialException,
        firstFailure: GetCredentialException? = null,
    ): ApiException {
        val failures = listOfNotNull(firstFailure, error)
            .map { it.javaClass.simpleName }
            .distinct()
            .joinToString(" → ")
        return ApiException(
            status = 0,
            code = "google_credential_failed",
            message = "Google sign-in could not be opened ($failures)",
        )
    }

    private fun androidx.credentials.Credential.toIdToken(): String {
        if (this !is CustomCredential || type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw ApiException(
                status = 0,
                code = "google_credential_type",
                message = "Google account was selected but no valid Google ID token was returned",
            )
        }
        return runCatching { GoogleIdTokenCredential.createFrom(data).idToken }
            .getOrElse {
                throw ApiException(
                    status = 0,
                    code = "google_token_parse",
                    message = "Google ID token could not be read",
                )
            }
    }
}
