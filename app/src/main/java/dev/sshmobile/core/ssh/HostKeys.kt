package dev.sshmobile.core.ssh

import dev.sshmobile.domain.model.HostKeyChallenge
import dev.sshmobile.domain.model.HostKeyInfo
import dev.sshmobile.domain.model.KnownHostKey
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** Computes the OpenSSH style "SHA256:..." fingerprint of an SSH public key blob. */
object HostKeys {
    fun fingerprint(keyBlob: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(keyBlob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    fun fingerprintOfBase64(keyBlobBase64: String): String =
        fingerprint(Base64.getDecoder().decode(keyBlobBase64))
}

/**
 * Strict known-hosts verification.
 *
 * - A key that matches the stored key of the same algorithm is accepted silently.
 * - An unknown key (new host or an additional algorithm) requires explicit user confirmation.
 * - A changed key for a known host is reported as [HostKeyChallenge.changed] and must be confirmed
 *   again; rejecting it aborts the handshake (MITM protection).
 */
class KnownHostsHostKeyVerifier(
    private val host: String,
    private val port: Int,
    knownKeys: List<KnownHostKey>,
    private val prompt: (HostKeyChallenge) -> Boolean,
    private val challengeId: () -> String = { UUID.randomUUID().toString() },
) : SshHostKeyVerifier {

    private val knownByType = knownKeys.associateBy { it.keyType }

    override fun verify(key: HostKeyInfo): Boolean {
        val existing = knownByType[key.keyType]
        if (existing != null && constantTimeEquals(existing.keyBlob, key.keyBlob)) {
            return true
        }
        val challenge = HostKeyChallenge(
            id = challengeId(),
            host = host,
            port = port,
            keyType = key.keyType,
            keyBlob = key.keyBlob,
            fingerprint = key.fingerprint,
            changed = existing != null,
            previousFingerprint = existing?.fingerprint,
        )
        return prompt(challenge)
    }

    override fun existingKeyTypes(): List<String> = knownByType.keys.toList()

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val ab = a.toByteArray()
        val bb = b.toByteArray()
        if (ab.size != bb.size) return false
        return MessageDigest.isEqual(ab, bb)
    }
}
