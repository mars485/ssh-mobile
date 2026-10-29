package dev.sshmobile.core.ssh

import dev.sshmobile.domain.model.HostKeyInfo
import dev.sshmobile.domain.model.ServerCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.PublicKey
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicReference

/**
 * SSHJ backed implementation of [SshEngine].
 *
 * All blocking work runs on [Dispatchers.IO]; callers interact with suspending functions only.
 * Host keys are never accepted implicitly: [KnownHostsHostKeyVerifier] decides, and the user is
 * asked when necessary.
 */
class SshjEngine : SshEngine {

    override suspend fun connect(
        config: SshConnectionConfig,
        verifier: SshHostKeyVerifier,
    ): SshConnection = withContext(Dispatchers.IO) {
        val client = SSHClient()
        val negotiatedKey = AtomicReference<HostKeyInfo?>(null)
        val existingTypes = verifier.existingKeyTypes()

        val hostKeyVerifier = object : HostKeyVerifier {
            override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
                if (key == null) return false
                val info = try {
                    keyInfo(key)
                } catch (t: Throwable) {
                    return false
                }
                if (!verifier.verify(info)) return false
                negotiatedKey.set(info)
                return true
            }

            override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = existingTypes
        }

        try {
            client.addHostKeyVerifier(hostKeyVerifier)
            client.connectTimeout = config.connectTimeoutMs
            client.connect(config.host, config.port)
            authenticate(client, config)
            SshjConnection(
                client = client,
                hostKey = negotiatedKey.get() ?: error("Host key was not verified"),
                terminalType = config.terminalType,
            )
        } catch (t: Throwable) {
            runCatching { client.close() }
            throw mapException(t)
        }
    }

    private fun authenticate(client: SSHClient, config: SshConnectionConfig) {
        when (val credentials = config.credentials) {
            is ServerCredentials.Password -> {
                client.authPassword(config.username, credentials.password)
            }

            is ServerCredentials.PrivateKey -> {
                val finder = credentials.passphrase?.let {
                    PasswordUtils.createOneOff(it.toCharArray())
                }
                val keyProvider = client.loadKeys(credentials.privateKeyPem, null, finder)
                client.authPublickey(config.username, keyProvider)
            }
        }
    }

    private fun keyInfo(key: PublicKey): HostKeyInfo {
        val keyType = try {
            KeyType.fromKey(key).toString()
        } catch (_: Throwable) {
            key.algorithm
        }
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        return HostKeyInfo(
            keyType = keyType,
            keyBlob = java.util.Base64.getEncoder().encodeToString(blob),
            fingerprint = HostKeys.fingerprint(blob),
        )
    }

    private fun mapException(t: Throwable): SshException {
        // Never include raw exception messages that might echo key material.
        val message = t.message.orEmpty()
        val lower = message.lowercase()
        return when (t) {
            is SshException -> t
            is UnknownHostException -> SshException(
                SshErrorKind.DNS,
                "Host not found. Check the address and your network connection.",
                t,
            )
            is SocketTimeoutException -> SshException(
                SshErrorKind.TIMEOUT,
                "Connection timed out. The host may be unreachable or firewalled.",
                t,
            )
            is ConnectException, is NoRouteToHostException -> SshException(
                SshErrorKind.REFUSED,
                "Connection refused. Check the host, port and firewall.",
                t,
            )
            is java.net.SocketException -> SshException(
                SshErrorKind.NETWORK,
                "Network error while connecting.",
                t,
            )
            is net.schmizz.sshj.userauth.UserAuthException -> SshException(
                SshErrorKind.AUTH,
                "Authentication failed. Check the username, password or key.",
                t,
            )
            is net.schmizz.sshj.transport.TransportException -> {
                if (lower.contains("host key") || lower.contains("verif")) {
                    SshException(SshErrorKind.HOST_KEY, "Host key verification failed.", t)
                } else {
                    SshException(SshErrorKind.PROTOCOL, "SSH handshake failed: $message", t)
                }
            }
            is IOException -> {
                if (lower.contains("key") || lower.contains("pem") || lower.contains("decrypt") ||
                    lower.contains("passphrase") || lower.contains("bouncycastle")
                ) {
                    SshException(
                        SshErrorKind.KEY_FORMAT,
                        "The private key could not be read. Check the key format and passphrase.",
                        t,
                    )
                } else if (lower.contains("auth")) {
                    SshException(SshErrorKind.AUTH, "Authentication failed.", t)
                } else {
                    SshException(SshErrorKind.NETWORK, "Connection failed: $message", t)
                }
            }
            else -> SshException(SshErrorKind.UNKNOWN, "Connection failed: ${t.javaClass.simpleName}", t)
        }
    }

    private class SshjConnection(
        private val client: SSHClient,
        override val hostKey: HostKeyInfo,
        private val terminalType: String,
    ) : SshConnection {

        override suspend fun openShell(cols: Int, rows: Int): SshShell = withContext(Dispatchers.IO) {
            val session = client.startSession()
            try {
                val modes = EnumMap<PTYMode, Int>(PTYMode::class.java)
                session.allocatePTY(terminalType, cols, rows, 0, 0, modes)
                val shell = session.startShell()
                SshjShell(session, shell)
            } catch (t: Throwable) {
                runCatching { session.close() }
                throw SshException(SshErrorKind.PROTOCOL, "Could not open a shell: ${t.message}", t)
            }
        }

        override fun close() {
            runCatching { client.close() }
        }

        override fun isConnected(): Boolean = runCatching { client.isConnected }.getOrDefault(false)
    }

    private class SshjShell(
        private val session: Session,
        private val shell: Session.Shell,
    ) : SshShell {
        override val input: InputStream = shell.inputStream
        override val output: OutputStream = shell.outputStream

        override suspend fun resize(cols: Int, rows: Int) = withContext(Dispatchers.IO) {
            shell.changeWindowDimensions(cols, rows, 0, 0)
        }

        override fun close() {
            runCatching { shell.close() }
            runCatching { session.close() }
        }
    }
}
