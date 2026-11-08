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
    val jumpHost: SshHopConfig? = null,
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

data class SshHopConfig(
    val host: String,
    val port: Int,
    val username: String,
    val credentials: ServerCredentials,
    val verifier: SshHostKeyVerifier,
    val connectTimeoutMs: Int = 15_000,
)

enum class PortForwardType { LOCAL, REMOTE }

/** SSH TCP-forwarding endpoints. A bind port of zero requests an available local/remote port. */
data class PortForwardSpec(
    val type: PortForwardType,
    val bindHost: String,
    val bindPort: Int,
    val destinationHost: String,
    val destinationPort: Int,
)

interface SshPortForward : AutoCloseable {
    val boundPort: Int
    override fun close()
}

/**
 * One remote file-system entry. [path] is absolute, [permissions] holds the POSIX mode bits
 * (for example 0b111101101 == 0755) and [modifiedAt] is epoch milliseconds.
 */
data class SftpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val permissions: Int,
    val modifiedAt: Long,
)

/** Replaceable SFTP transport. Every call is suspending and runs off the main thread. */
interface SshSftp : AutoCloseable {
    suspend fun canonicalize(path: String): String

    suspend fun list(path: String): List<SftpEntry>

    suspend fun stat(path: String): SftpEntry

    /** Creates [path] and any missing parents. */
    suspend fun mkdir(path: String)

    suspend fun rename(from: String, to: String)

    suspend fun remove(path: String, recursive: Boolean)

    suspend fun chmod(path: String, mode: Int)

    suspend fun download(remotePath: String, sink: OutputStream, onProgress: (Long) -> Unit = {})

    suspend fun upload(remotePath: String, source: InputStream, onProgress: (Long) -> Unit = {})

    override fun close()
}

/** Replaceable SSH transport. The rest of the app only depends on this interface. */
interface SshEngine {
    suspend fun connect(config: SshConnectionConfig, verifier: SshHostKeyVerifier): SshConnection
}

interface SshConnection : AutoCloseable {
    val hostKey: HostKeyInfo

    suspend fun openShell(cols: Int, rows: Int): SshShell

    suspend fun startPortForward(spec: PortForwardSpec): SshPortForward

    /** Opens an SFTP subsystem channel over this connection. */
    suspend fun openSftp(): SshSftp

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
