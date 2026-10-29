package dev.sshmobile.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts secrets at rest. Implementations must never log plaintext and must produce an opaque,
 * self-describing string that can be stored in SQLite.
 */
interface SecretCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(payload: String): String
}

class SecretCipherException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * AES-256-GCM with a non-exportable key held by the Android Keystore (StrongBox is used when the
 * device supports it). The key is generated on first use and never leaves secure hardware / TEE
 * under normal operation.
 *
 * The payload format is "v1:<base64 iv>:<base64 ciphertext>". Authentication is provided by GCM,
 * therefore tampering or copying the database to another device results in a decrypt failure.
 */
class AndroidKeystoreSecretCipher(
    private val alias: String = DEFAULT_ALIAS,
) : SecretCipher {

    override fun encrypt(plaintext: String): String {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            return "$VERSION:${encode(cipher.iv)}:${encode(ciphertext)}"
        } catch (t: Throwable) {
            throw SecretCipherException("Unable to encrypt secret with the Android Keystore", t)
        }
    }

    override fun decrypt(payload: String): String {
        val parts = payload.split(':')
        if (parts.size != 3 || parts[0] != VERSION) {
            throw SecretCipherException("Unsupported secret payload")
        }
        try {
            val iv = decode(parts[1])
            val ciphertext = decode(parts[2])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (t: Throwable) {
            throw SecretCipherException("Unable to decrypt secret with the Android Keystore", t)
        }
    }

    /** Called by "wipe credentials" in Settings. */
    fun deleteKey() {
        try {
            val keyStore = keyStore()
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } catch (_: Throwable) {
            // Nothing useful can be done; the caller reports that data was wiped.
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = keyStore()
        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        try {
            builder.setIsStrongBoxBacked(true)
            generator.init(builder.build())
            return generator.generateKey()
        } catch (_: Throwable) {
            // StrongBox is optional; fall back to the TEE-backed key.
            generator.init(builder.setIsStrongBoxBacked(false).build())
            return generator.generateKey()
        }
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)

    companion object {
        const val DEFAULT_ALIAS = "sshmobile.secrets.v1"
        private const val VERSION = "v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
