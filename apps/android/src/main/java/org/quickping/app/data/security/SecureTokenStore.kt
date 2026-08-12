package org.quickping.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtMillis: Long,
)

class SecureTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun installationId(): String {
        preferences.getString(KEY_INSTALLATION_ID, null)?.let { return it }
        return UUID.randomUUID().toString().also {
            preferences.edit().putString(KEY_INSTALLATION_ID, it).apply()
        }
    }

    fun save(session: StoredSession) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, encrypt(session.accessToken))
            .putString(KEY_REFRESH_TOKEN, encrypt(session.refreshToken))
            .putLong(KEY_ACCESS_EXPIRY, session.accessExpiresAtMillis)
            .apply()
    }

    fun load(): StoredSession? = runCatching {
        val access = preferences.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refresh = preferences.getString(KEY_REFRESH_TOKEN, null) ?: return null
        StoredSession(
            accessToken = decrypt(access),
            refreshToken = decrypt(refresh),
            accessExpiresAtMillis = preferences.getLong(KEY_ACCESS_EXPIRY, 0),
        )
    }.getOrElse {
        clear()
        null
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ACCESS_EXPIRY)
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + ciphertext.size)
        cipher.iv.copyInto(payload)
        ciphertext.copyInto(payload, cipher.iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String {
        val decoded = Base64.decode(payload, Base64.NO_WRAP)
        require(decoded.size > IV_SIZE)
        val iv = decoded.copyOfRange(0, IV_SIZE)
        val ciphertext = decoded.copyOfRange(IV_SIZE, decoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES = "quickping_secure_session"
        const val KEY_ALIAS = "quickping_session_v1"
        const val KEY_INSTALLATION_ID = "installation_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ACCESS_EXPIRY = "access_expiry"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
