package dev.sshmobile.core.ssh

import dev.sshmobile.domain.model.HostKeyInfo
import dev.sshmobile.domain.model.ServerCredentials
import java.io.InputStream
import java.io.OutputStream

data class SshConnectionConfig(
    val host: String,
    val port: Int,
    val username: String,
    val credentials: ServerCredentials,
    val connectTimeoutMs: Int = 15_000,
    val terminalType: String = "xterm-256color",
)

/**
 * Verification callback invoked during the key exchange. Implementations decide whether the
 * presented host key is trusted. Returning false aborts the connection.
 */
interface SshHostKeyVerifier {
    fun verify(key: HostKeyInfo): Boolean

    /** Key types already trusted for this host, used by the client to negotiate a known key. */
    fun existingKeyTypes(): List<String> = emptyList()
}

/** Replaceable SSH transport. The rest of the app only depends on this interface. */
interface SshEngine {
    suspend fun connect(config: SshConnectionConfig, verifier: SshHostKeyVerifier): SshConnection
}

interface SshConnection : AutoCloseable {
    val hostKey: HostKeyInfo

    suspend fun openShell(cols: Int, rows: Int): SshShell

    /** Transport liveness, used to distinguish a clean shell exit from a dropped connection. */
    fun isConnected(): Boolean

    override fun close()
}

interface SshShell : AutoCloseable {
    val input: InputStream
    val output: OutputStream

    suspend fun resize(cols: Int, rows: Int)

    override fun close()
}

enum class SshErrorKind {
    DNS,
    TIMEOUT,
    REFUSED,
    AUTH,
    HOST_KEY,
    KEY_FORMAT,
    PROTOCOL,
    NETWORK,
    UNKNOWN,
}

class SshException(
    val kind: SshErrorKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
