package dev.sshmobile.core.database.entity

import androidx.room.Entity

/**
 * Trusted server host keys. The blob is the SSH wire-format public key (RFC 4253), base64 encoded.
 * Keyed per host, port and key algorithm so that a server offering both RSA and ED25519 host keys
 * is handled correctly.
 */
@Entity(
    tableName = "known_hosts",
    primaryKeys = ["host", "port", "keyType"],
)
data class KnownHostEntity(
    val host: String,
    val port: Int,
    val keyType: String,
    val keyBlob: String,
    val fingerprint: String,
    val addedAt: Long,
    val lastSeenAt: Long,
)

data class KnownHostCount(val count: Int)
