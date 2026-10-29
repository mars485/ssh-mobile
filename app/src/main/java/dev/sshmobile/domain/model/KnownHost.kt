package dev.sshmobile.domain.model

data class KnownHostKey(
    val host: String,
    val port: Int,
    val keyType: String,
    val keyBlob: String,
    val fingerprint: String,
    val addedAt: Long = 0,
    val lastSeenAt: Long = 0,
)

/** Description of the key presented by a server during the handshake. */
data class HostKeyInfo(
    val keyType: String,
    val keyBlob: String,
    val fingerprint: String,
)

data class HostKeyChallenge(
    val id: String,
    val host: String,
    val port: Int,
    val keyType: String,
    val keyBlob: String,
    val fingerprint: String,
    val changed: Boolean,
    val previousFingerprint: String?,
)
