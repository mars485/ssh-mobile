package dev.sshmobile.core.security

import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Android ships a stripped down "BC" provider. SSHJ needs the full BouncyCastle implementation for
 * modern key exchange, Ed25519/ECDSA host keys and key file parsing, so the bundled provider is
 * installed at the highest priority. This is the recommended workaround for the Android P+
 * provider changes and does not weaken certificate/host-key validation.
 */
object CryptoProviders {
    private const val TAG = "CryptoProviders"

    fun install() {
        try {
            val provider = BouncyCastleProvider()
            Security.removeProvider(provider.name)
            Security.insertProviderAt(provider, 1)
        } catch (t: Throwable) {
            // A failure here is non-fatal: the system providers may still be sufficient for
            // RSA/ECDSA servers. The connection attempt will surface a clear error if not.
            Log.w(TAG, "BouncyCastle provider could not be installed: ${t.javaClass.simpleName}")
        }
    }
}
