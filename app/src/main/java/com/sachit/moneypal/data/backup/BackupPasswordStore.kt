package com.sachit.moneypal.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the auto-backup password (plan 011) in app-private SharedPreferences,
 * wrapped by an AES-256-GCM key that lives in the Android Keystore
 * (non-exportable, dies with the device — appropriate here because the
 * wrapped secret only needs to be readable by this app on this device).
 *
 * Deliberately NOT androidx.security-crypto: that library is deprecated and
 * this is its minimal Keystore equivalent for one small secret.
 */
@Singleton
class BackupPasswordStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "moneypal_auto_backup_password_key"
        private const val PREFS_NAME = "moneypal_backup_password"
        private const val PREF_KEY = "wrapped_password"
        private const val GCM_TAG_BITS = 128
        private const val IV_LENGTH_BYTES = 12
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun ensureKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun savePassword(password: CharArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, ensureKey())
        val wrapped = cipher.iv + cipher.doFinal(String(password).toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(PREF_KEY, java.util.Base64.getEncoder().encodeToString(wrapped))
            .apply()
    }

    /** Returns the stored password, or null when none was saved. */
    fun loadPassword(): CharArray? {
        val wrappedB64 = prefs.getString(PREF_KEY, null) ?: return null
        val wrapped = java.util.Base64.getDecoder().decode(wrappedB64)
        val iv = wrapped.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = wrapped.copyOfRange(IV_LENGTH_BYTES, wrapped.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, ensureKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8).toCharArray()
    }

    fun hasPassword(): Boolean = prefs.contains(PREF_KEY)

    fun clearPassword() {
        prefs.edit().remove(PREF_KEY).apply()
    }
}
