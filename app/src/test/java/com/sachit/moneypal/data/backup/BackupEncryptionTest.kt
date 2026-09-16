package com.sachit.moneypal.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEncryptionTest {

    private fun longJson(): String {
        val builder = StringBuilder("{")
        for (i in 0 until 500) {
            builder.append("\"key$i\":\"value with unicode €éñ and numbers 12345$i\",")
        }
        builder.append("\"end\":true}")
        return builder.toString()
    }

    @Test
    fun `round trip encrypt and decrypt`() {
        val password = "correct horse battery".toCharArray()
        val encrypted = BackupEncryption.encrypt(longJson(), password)
        assertTrue(BackupEncryption.isEncrypted(encrypted))
        assertEquals(longJson(), BackupEncryption.decrypt(encrypted, password))
    }

    @Test
    fun `round trip with large 10k char json body`() {
        val big = "x".repeat(10_000) + longJson()
        val password = "passw0rd!".toCharArray()
        val encrypted = BackupEncryption.encrypt(big, password)
        assertEquals(big, BackupEncryption.decrypt(encrypted, password))
    }

    @Test
    fun `wrong password throws BackupFormatException`() {
        val encrypted = BackupEncryption.encrypt("hello", "aaa".toCharArray())
        try {
            BackupEncryption.decrypt(encrypted, "bbb".toCharArray())
            throw AssertionError("Expected BackupFormatException")
        } catch (e: BackupFormatException) {
            assertEquals("Wrong password or corrupted backup", e.message)
        }
    }

    @Test
    fun `corrupted ciphertext throws BackupFormatException`() {
        val password = "password".toCharArray()
        val encrypted = BackupEncryption.encrypt("secret data", password)
        // Corrupt a base64 char in the MIDDLE of the payload (inside the
        // ciphertext, far from the tail). Flipping a char near the end only
        // perturbs bits of the 16-byte GCM tag — the tag can still verify,
        // so the old tail-index approach failed intermittently (~50%). A
        // mid-payload bit flip always changes ciphertext bytes, which GCM
        // must reject.
        val bodyStart = BackupEncryption.MAGIC.length
        val corruptIndex = bodyStart + (encrypted.length - bodyStart) / 2
        val swappedChar = if (encrypted[corruptIndex] == 'A') 'B' else 'A'
        val corrupted = encrypted.substring(0, corruptIndex) +
            swappedChar + encrypted.substring(corruptIndex + 1)
        try {
            BackupEncryption.decrypt(corrupted, password)
            throw AssertionError("Expected BackupFormatException")
        } catch (_: BackupFormatException) {
        }
    }

    @Test
    fun `two encryptions of same input differ`() {
        val password = "same".toCharArray()
        val a = BackupEncryption.encrypt("data", password)
        val b = BackupEncryption.encrypt("data", password)
        assertTrue(a != b)
        assertEquals("data", BackupEncryption.decrypt(a, password))
        assertEquals("data", BackupEncryption.decrypt(b, password))
    }

    @Test
    fun `isEncrypted distinguishes plaintext and encrypted`() {
        assertFalse(BackupEncryption.isEncrypted("{\"schemaVersion\":1}"))
        assertTrue(BackupEncryption.isEncrypted(BackupEncryption.encrypt("{}", "p".toCharArray())))
    }

    @Test
    fun `empty password is rejected`() {
        try {
            BackupEncryption.encrypt("data", CharArray(0))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `unicode password round trips`() {
        val password = "p@ššwörd€🔒".toCharArray()
        val encrypted = BackupEncryption.encrypt(longJson(), password)
        assertEquals(longJson(), BackupEncryption.decrypt(encrypted, password))
    }
}
