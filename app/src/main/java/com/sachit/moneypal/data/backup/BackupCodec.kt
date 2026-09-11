package com.sachit.moneypal.data.backup

import kotlinx.serialization.json.Json

/**
 * JSON codec for [MoneyPalBackup]. `ignoreUnknownKeys` keeps older backups
 * restorable into newer schemas; unknown schema versions are rejected.
 */
object BackupCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: MoneyPalBackup): String = json.encodeToString(MoneyPalBackup.serializer(), backup)

    fun decode(raw: String): MoneyPalBackup {
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
