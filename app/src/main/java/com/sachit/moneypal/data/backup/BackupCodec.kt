package com.sachit.moneypal.data.backup

import kotlinx.serialization.json.Json

/**
 * JSON codec for [MoneyPalBackup]. `ignoreUnknownKeys` keeps older backups
 * restorable into newer schemas; unknown schema versions are rejected.
 *
 * Encrypted files (plan 011) carry the `MONEYPAL_ENC1:` prefix and wrap this
 * JSON via [BackupEncryption]. [decode] dispatches on the prefix so both
 * formats restore transparently; a password-protected file without a password
 * throws the dedicated "password-protected" error the UI prompts on.
 */
object BackupCodec {

    /** Error message the restore UI matches on to prompt for a password. */
    const val BACKUP_PASSWORD_REQUIRED_MESSAGE = "This backup is password-protected"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: MoneyPalBackup): String = json.encodeToString(MoneyPalBackup.serializer(), backup)

    fun encodeEncrypted(backup: MoneyPalBackup, password: CharArray): String =
        BackupEncryption.encrypt(encode(backup), password)

    fun decode(raw: String): MoneyPalBackup = decode(raw, password = null)

    fun decode(raw: String, password: CharArray?): MoneyPalBackup {
        if (BackupEncryption.isEncrypted(raw)) {
            if (password == null) {
                throw BackupFormatException(BACKUP_PASSWORD_REQUIRED_MESSAGE)
            }
            return decodePlaintext(BackupEncryption.decrypt(raw, password))
        }
        // Plaintext file; an unused password stays lenient for the caller.
        return decodePlaintext(raw)
    }

    private fun decodePlaintext(raw: String): MoneyPalBackup {
        val backup = try {
            json.decodeFromString(MoneyPalBackup.serializer(), raw)
        } catch (e: Exception) {
            throw BackupFormatException("Not a valid MoneyPal backup file: ${e.message}")
        }
        if (backup.schemaVersion > MoneyPalBackup.CURRENT_SCHEMA_VERSION) {
            throw BackupFormatException(
                "Backup schema v${backup.schemaVersion} is newer than supported " +
                    "v${MoneyPalBackup.CURRENT_SCHEMA_VERSION}. Update the app first."
            )
        }
        if (backup.schemaVersion < 1) {
            throw BackupFormatException("Backup schema version missing")
        }
        return backup
    }
}
