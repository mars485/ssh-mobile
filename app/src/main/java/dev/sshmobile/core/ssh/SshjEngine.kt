package dev.sshmobile.core.ssh

import dev.sshmobile.domain.model.HostKeyInfo
import dev.sshmobile.domain.model.ServerCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.connection.channel.Channel
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.forwarded.ConnectListener
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.PublicKey
import java.util.EnumMap
import java.util.EnumSet
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
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
        var jumpClient: SSHClient? = null

        try {
            client.connectTimeout = config.connectTimeoutMs
            addHostKeyVerifier(client, verifier, negotiatedKey)

            val jumpHost = config.jumpHost
            if (jumpHost == null) {
                client.connect(config.host, config.port)
            } else {
                val proxy = SSHClient()
                jumpClient = proxy
                proxy.connectTimeout = jumpHost.connectTimeoutMs
                addHostKeyVerifier(proxy, jumpHost.verifier, AtomicReference())
                proxy.connect(jumpHost.host, jumpHost.port)
                authenticate(proxy, jumpHost.username, jumpHost.credentials)
                client.connectVia(proxy.newDirectConnection(config.host, config.port))
            }

            authenticate(client, config.username, config.credentials)
            SshjConnection(
                client = client,
                jumpClient = jumpClient,
                hostKey = negotiatedKey.get() ?: error("Host key was not verified"),
                terminalType = config.terminalType,
            )
        } catch (t: Throwable) {
            runCatching { client.close() }
            runCatching { jumpClient?.close() }
            throw mapException(t)
        }
    }

    private fun addHostKeyVerifier(
        client: SSHClient,
        verifier: SshHostKeyVerifier,
        negotiatedKey: AtomicReference<HostKeyInfo?>,
    ) {
        val existingTypes = verifier.existingKeyTypes()
        client.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
                if (key == null) return false
                val info = try {
                    keyInfo(key)
                } catch (_: Throwable) {
                    return false
                }
                if (!verifier.verify(info)) return false
                negotiatedKey.set(info)
                return true
            }

            override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = existingTypes
        })
    }

    private fun authenticate(client: SSHClient, username: String, credentials: ServerCredentials) {
        when (credentials) {
            is ServerCredentials.Password -> {
                client.authPassword(username, credentials.password)
            }

            is ServerCredentials.PrivateKey -> {
                val finder = credentials.passphrase?.let {
                    PasswordUtils.createOneOff(it.toCharArray())
                }
                val keyProvider = client.loadKeys(credentials.privateKeyPem, null, finder)
                client.authPublickey(username, keyProvider)
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
        private val jumpClient: SSHClient?,
        override val hostKey: HostKeyInfo,
        private val terminalType: String,
    ) : SshConnection {
        private val forwardLock = Any()
        private val activeForwards = linkedSetOf<SshjPortForward>()

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

        override suspend fun startPortForward(spec: PortForwardSpec): SshPortForward = withContext(Dispatchers.IO) {
            require(spec.bindHost.isNotBlank()) { "Forward bind host is required" }
            require(spec.destinationHost.isNotBlank()) { "Forward destination host is required" }
            require(spec.bindPort in 0..65535) { "Forward bind port is invalid" }
            require(spec.destinationPort in 1..65535) { "Forward destination port is invalid" }
            check(client.isConnected) { "SSH session is not connected" }

            when (spec.type) {
                PortForwardType.LOCAL -> startLocalForward(spec)
                PortForwardType.REMOTE -> startRemoteForward(spec)
            }
        }

        override suspend fun openSftp(): SshSftp = withContext(Dispatchers.IO) {
            check(client.isConnected) { "SSH session is not connected" }
            try {
                SshjSftp(client.newSFTPClient())
            } catch (t: Throwable) {
                throw SshException(
                    SshErrorKind.PROTOCOL,
                    "The server refused the SFTP subsystem: ${t.message}",
                    t,
                )
            }
        }

        private fun startLocalForward(spec: PortForwardSpec): SshPortForward {
            val serverSocket = ServerSocket()
            serverSocket.reuseAddress = true
            try {
                serverSocket.bind(InetSocketAddress(spec.bindHost, spec.bindPort))
                val boundPort = serverSocket.localPort
                // sshj's Parameters order is (localHost, localPort, remoteHost, remotePort):
                // the first pair is the originator, the second is the forwarding destination.
                val parameters = Parameters(
                    spec.bindHost,
                    boundPort,
                    spec.destinationHost,
                    spec.destinationPort,
                )
                val forwarder = client.newLocalPortForwarder(parameters, serverSocket)
                val listenerThread = Thread(
                    { runCatching { forwarder.listen() } },
                    "ssh-local-forward-$boundPort",
                ).apply {
                    isDaemon = true
                    start()
                }
                return registerForward(boundPort) {
                    runCatching { forwarder.close() }
                    runCatching { serverSocket.close() }
                    listenerThread.interrupt()
                }
            } catch (t: Throwable) {
                runCatching { serverSocket.close() }
                throw t
            }
        }

        private fun startRemoteForward(spec: PortForwardSpec): SshPortForward {
            val remotePortForwarder = client.remotePortForwarder
            val requested = RemotePortForwarder.Forward(spec.bindHost, spec.bindPort)
            val bound = remotePortForwarder.bind(
                requested,
                object : ConnectListener {
                    override fun gotConnect(channel: Channel.Forwarded) {
                        Thread(
                            { relayForwardedChannel(channel, spec.destinationHost, spec.destinationPort) },
                            "ssh-remote-forward-relay",
                        ).apply {
                            isDaemon = true
                            start()
                        }
                    }
                },
            )
            return registerForward(bound.port) {
                runCatching { remotePortForwarder.cancel(bound) }
            }
        }

        private fun registerForward(boundPort: Int, closeAction: () -> Unit): SshPortForward {
            lateinit var handle: SshjPortForward
            handle = SshjPortForward(boundPort) {
                synchronized(forwardLock) { activeForwards.remove(handle) }
                closeAction()
            }
            synchronized(forwardLock) {
                check(client.isConnected) { "SSH session is not connected" }
                activeForwards += handle
            }
            return handle
        }

        private fun relayForwardedChannel(channel: Channel.Forwarded, host: String, port: Int) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), FORWARD_CONNECT_TIMEOUT_MS)
                val channelToSocket = Thread({
                    runCatching { channel.inputStream.copyTo(socket.getOutputStream()) }
                    runCatching { socket.shutdownOutput() }
                }, "ssh-forwarded-to-local")
                val socketToChannel = Thread({
                    runCatching {
                        socket.getInputStream().copyTo(channel.outputStream)
                        channel.outputStream.flush()
                    }
                    runCatching { channel.close() }
                }, "ssh-local-to-forwarded")
                channelToSocket.isDaemon = true
                socketToChannel.isDaemon = true
                channelToSocket.start()
                socketToChannel.start()
                channelToSocket.join()
                socketToChannel.join()
            } catch (_: Throwable) {
                // The forwarded connection is isolated; a failed local service must not kill SSH.
            } finally {
                runCatching { socket.close() }
                runCatching { channel.close() }
            }
        }

        private fun closePortForwards() {
            val forwards = synchronized(forwardLock) {
                activeForwards.toList().also { activeForwards.clear() }
            }
            forwards.forEach { it.close() }
        }

        override fun close() {
            closePortForwards()
            runCatching { client.close() }
            runCatching { jumpClient?.close() }
        }

        override fun isConnected(): Boolean =
            runCatching { client.isConnected && (jumpClient?.isConnected ?: true) }.getOrDefault(false)

        private class SshjPortForward(
            override val boundPort: Int,
            private val closeAction: () -> Unit,
        ) : SshPortForward {
            private val closed = AtomicBoolean(false)

            override fun close() {
                if (closed.compareAndSet(false, true)) closeAction()
            }
        }
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

    private class SshjSftp(
        private val sftp: SFTPClient,
    ) : SshSftp {
        override suspend fun canonicalize(path: String): String = withContext(Dispatchers.IO) {
            sftp.canonicalize(path)
        }

        override suspend fun list(path: String): List<SftpEntry> = withContext(Dispatchers.IO) {
            sftp.ls(path)
                .asSequence()
                .filter { it.name != "." && it.name != ".." }
                .map { it.toEntry() }
                .sortedWith(
                    compareByDescending<SftpEntry> { it.isDirectory }
                        .thenBy { it.name.lowercase() },
                )
                .toList()
        }

        override suspend fun stat(path: String): SftpEntry = withContext(Dispatchers.IO) {
            sftp.stat(path).toEntry(path)
        }

        override suspend fun mkdir(path: String): Unit = withContext(Dispatchers.IO) {
            sftp.mkdirs(path)
        }

        override suspend fun rename(from: String, to: String): Unit = withContext(Dispatchers.IO) {
            sftp.rename(from, to)
        }

        override suspend fun remove(path: String, recursive: Boolean): Unit =
            withContext(Dispatchers.IO) {
                removeInternal(path, recursive)
            }

        private fun removeInternal(path: String, recursive: Boolean) {
            val attributes = sftp.statExistence(path) ?: return
            if (attributes.type == FileMode.Type.DIRECTORY) {
                if (recursive) {
                    sftp.ls(path)
                        .filter { it.name != "." && it.name != ".." }
                        .forEach { removeInternal(it.path, true) }
                }
                sftp.rmdir(path)
            } else {
                sftp.rm(path)
            }
        }

        override suspend fun chmod(path: String, mode: Int): Unit = withContext(Dispatchers.IO) {
            sftp.chmod(path, mode and PERMISSION_MASK)
        }

        override suspend fun download(
            remotePath: String,
            sink: OutputStream,
            onProgress: (Long) -> Unit,
        ): Unit = withContext(Dispatchers.IO) {
            val file = sftp.open(remotePath, EnumSet.of(OpenMode.READ))
            try {
                val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
                var offset = 0L
                while (true) {
                    val read = file.read(offset, buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    sink.write(buffer, 0, read)
                    offset += read
                    onProgress(offset)
                }
                sink.flush()
            } finally {
                runCatching { file.close() }
            }
        }

        override suspend fun upload(
            remotePath: String,
            source: InputStream,
            onProgress: (Long) -> Unit,
        ): Unit = withContext(Dispatchers.IO) {
            val file = sftp.open(
                remotePath,
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC),
            )
            try {
                val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
                var offset = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    file.write(offset, buffer, 0, read)
                    offset += read
                    onProgress(offset)
                }
            } finally {
                runCatching { file.close() }
            }
        }

        override fun close() {
            runCatching { sftp.close() }
        }

        private fun RemoteResourceInfo.toEntry(): SftpEntry {
            val attributes = attributes
            return SftpEntry(
                name = name,
                path = path,
                isDirectory = isDirectory,
                isSymlink = attributes.type == FileMode.Type.SYMLINK,
                size = attributes.size,
                permissions = attributes.mode.permissionsMask and PERMISSION_MASK,
                modifiedAt = attributes.mtime * 1000L,
            )
        }

        private fun FileAttributes.toEntry(path: String): SftpEntry = SftpEntry(
            name = path.trimEnd('/').substringAfterLast('/').ifEmpty { path },
            path = path,
            isDirectory = type == FileMode.Type.DIRECTORY,
            isSymlink = type == FileMode.Type.SYMLINK,
            size = size,
            permissions = mode.permissionsMask and PERMISSION_MASK,
            modifiedAt = mtime * 1000L,
        )
    }

    private companion object {
        const val FORWARD_CONNECT_TIMEOUT_MS = 10_000
        const val TRANSFER_BUFFER_BYTES = 32 * 1024
        const val PERMISSION_MASK = 0x1FF
    }
}
