package com.sachit.moneypal.data.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional password encryption for backup files (plan 011).
 *
 * File format: `MONEYPAL_ENC1:<base64( salt[16] || iv[12] || ciphertext )>`
 * - AES-256-GCM (authenticates the whole payload; wrong password or corruption
 *   fails decryption, never yields garbage)
 * - PBKDF2WithHmacSHA256 key derivation, 210,000 iterations, 16-byte random
 *   salt per file, 12-byte random IV per file
 *
 * The magic prefix is the scheme version marker: a future scheme changes the
 * prefix to `MONEYPAL_ENC2:` and decryption dispatches on it. Plaintext JSON
 * backups never carry the prefix, so [isEncrypted] cleanly separates the two
 * formats and keeps `BackupCodec.decode` unambiguous.
 *
 * Passwords are never logged and never persisted by this object.
 */
object BackupEncryption {

    /** Version marker for the AES-256-GCM scheme. */
    const val MAGIC = "MONEYPAL_ENC1:"

    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000

    fun isEncrypted(raw: String): Boolean = raw.startsWith(MAGIC)

    fun encrypt(plaintext: String, password: CharArray): String {
        require(password.isNotEmpty()) { "Password must not be empty" }

        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val payload = salt + iv + ciphertext
        // java.util.Base64 (API 26+): identical output on device and in JVM
        // unit tests, unlike android.util.Base64 which is stubbed on the JVM.
        return MAGIC + Base64.getEncoder().encodeToString(payload)
    }

    /** @throws BackupFormatException on a wrong password or a corrupted file. */
    fun decrypt(encoded: String, password: CharArray): String {
        if (!isEncrypted(encoded)) {
            throw BackupFormatException("Not an encrypted MoneyPal backup")
        }
        val payload = try {
            Base64.getDecoder().decode(encoded.removePrefix(MAGIC))
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Corrupted backup payload: ${e.message}")
        }
        val minSize = SALT_LENGTH_BYTES + IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / 8
        if (payload.size < minSize) {
            throw BackupFormatException("Corrupted backup payload: too short")
        }

        val salt = payload.copyOfRange(0, SALT_LENGTH_BYTES)
        val iv = payload.copyOfRange(SALT_LENGTH_BYTES, SALT_LENGTH_BYTES + IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(SALT_LENGTH_BYTES + IV_LENGTH_BYTES, payload.size)
        val key = deriveKey(password, salt)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // GCM auth failure (wrong password) and truncated ciphertext both
            // land here; callers must not be able to distinguish them.
            throw BackupFormatException("Wrong password or corrupted backup")
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }
}
