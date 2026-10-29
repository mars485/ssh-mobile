package dev.sshmobile.core.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.userauth.password.PasswordUtils

data class PrivateKeySummary(
    val keyType: String,
    val fingerprint: String,
) {
    val display: String
        get() = "${keyType.uppercase()} · ${fingerprint.take(24)}…"
}

/**
 * Parses an SSH private key with the same code path used for authentication. Used when the user
 * imports a key so that format/passphrase errors are reported immediately and a fingerprint can be
 * shown without storing anything in plaintext.
 */
object PrivateKeyInspector {

    fun inspect(privateKeyPem: String, passphrase: String?): PrivateKeySummary {
        val client = SSHClient()
        try {
            val finder = passphrase?.takeIf { it.isNotEmpty() }?.let {
                PasswordUtils.createOneOff(it.toCharArray())
            }
            val provider = client.loadKeys(privateKeyPem, null, finder)
            val publicKey = provider.getPublic()
            val blob = Buffer.PlainBuffer().putPublicKey(publicKey).compactData
            return PrivateKeySummary(
                keyType = provider.type.toString(),
                fingerprint = HostKeys.fingerprint(blob),
            )
        } finally {
            runCatching { client.close() }
        }
    }
}
