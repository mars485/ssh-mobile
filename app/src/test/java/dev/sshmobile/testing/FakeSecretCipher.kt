package dev.sshmobile.testing

import dev.sshmobile.core.security.SecretCipher
import java.util.Base64

/** Deterministic, reversible cipher for JVM tests that must not touch the Android Keystore. */
class FakeSecretCipher : SecretCipher {
    override fun encrypt(plaintext: String): String =
        PREFIX + Base64.getEncoder().encodeToString(plaintext.toByteArray(Charsets.UTF_8))

    override fun decrypt(payload: String): String {
        require(payload.startsWith(PREFIX)) { "unexpected payload" }
        return String(Base64.getDecoder().decode(payload.removePrefix(PREFIX)), Charsets.UTF_8)
    }

    companion object {
        const val PREFIX = "test-enc:"
    }
}
