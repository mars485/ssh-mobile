package dev.sshmobile.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted server definition. Secrets are stored only as [secret] / [passphrase] ciphertext
 * produced by [dev.sshmobile.core.security.SecretCipher]; no plaintext credentials are persisted.
 */
@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String,
    val groupName: String?,
    val favorite: Boolean,
    val secret: String?,
    val passphrase: String?,
    val keySummary: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
