package dev.sshmobile.core.session

import dev.sshmobile.core.ssh.KnownHostsHostKeyVerifier
import dev.sshmobile.core.ssh.SftpEntry
import dev.sshmobile.core.ssh.SshConnection
import dev.sshmobile.core.ssh.SshConnectionConfig
import dev.sshmobile.core.ssh.SshEngine
import dev.sshmobile.core.ssh.SshErrorKind
import dev.sshmobile.core.ssh.SshException
import dev.sshmobile.core.ssh.SshHopConfig
import dev.sshmobile.core.ssh.SshSftp
import dev.sshmobile.data.repository.CredentialsUnavailableException
import dev.sshmobile.data.repository.KnownHostRepository
import dev.sshmobile.data.repository.ServerRepository
import dev.sshmobile.domain.model.HostKeyChallenge
import dev.sshmobile.domain.model.KnownHostKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Application scoped SFTP session. It reuses the same encrypted credential storage and strict
 * host-key verification as the terminal: connecting to a new host asks for fingerprint
 * confirmation before any file operation is allowed.
 *
 * Only one server is connected at a time. Directory operations are serialized because the
 * underlying SFTP channel is stateful; transfers open their own subsystem channel so a large
 * upload never blocks browsing.
 */
class SftpSessionManager(
    private val engine: SshEngine,
    private val serverRepository: ServerRepository,
    private val knownHostRepository: KnownHostRepository,
    private val scope: CoroutineScope,
) {
    private data class PendingPrompt(
        val challenge: HostKeyChallenge,
        val latch: CountDownLatch,
        val result: AtomicBoolean,
    )

    private val _state = MutableStateFlow(SftpState())
    val state: StateFlow<SftpState> = _state.asStateFlow()

    private val directoryMutex = Mutex()
    private val errorIds = AtomicLong(0)

    @Volatile
    private var connection: SshConnection? = null

    @Volatile
    private var sftp: SshSftp? = null

    @Volatile
    private var pendingPrompt: PendingPrompt? = null

    @Volatile
    private var hostKeyRejected = false

    @Volatile
    private var hostKeyTimedOut = false

    @Volatile
    private var userDisconnected = false

    private var connectJob: Job? = null

    fun connect(serverId: Long) {
        disconnect()
        userDisconnected = false
        connectJob?.cancel()
        connectJob = scope.launch { connectInternal(serverId) }
    }

    fun disconnect() {
        userDisconnected = true
        connectJob?.cancel()
        connectJob = null
        releasePendingPrompt()
        closeTransport()
        _state.update {
            it.copy(
                status = SftpStatus.DISCONNECTED,
                hostKeyPrompt = null,
                loading = false,
                entries = emptyList(),
            )
        }
    }

    fun respondToHostKey(challengeId: String, accept: Boolean) {
        val pending = pendingPrompt ?: return
        if (pending.challenge.id != challengeId) return
        pendingPrompt = null
        scope.launch {
            if (accept) {
                runCatching {
                    knownHostRepository.save(
                        KnownHostKey(
                            host = pending.challenge.host,
                            port = pending.challenge.port,
                            keyType = pending.challenge.keyType,
                            keyBlob = pending.challenge.keyBlob,
                            fingerprint = pending.challenge.fingerprint,
                        ),
                    )
                }
            } else {
                hostKeyRejected = true
            }
            pending.result.set(accept)
            _state.update { it.copy(hostKeyPrompt = null) }
            pending.latch.countDown()
        }
    }

    // ------------------------------------------------------------ navigation

    fun open(path: String) {
        val client = sftp ?: return
        scope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val entries = directoryMutex.withLock { client.list(path) }
                _state.update {
                    it.copy(path = path, entries = entries, loading = false, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false) }
                report(SftpOperation.LIST, t)
            }
        }
    }

    fun refresh() {
        if (sftp == null) return
        open(_state.value.path)
    }

    fun up() {
        val current = _state.value.path
        val parent = parentOf(current)
        if (parent != current) open(parent)
    }

    // --------------------------------------------------------------- mutation

    fun mkdir(name: String) {
        val clean = validateName(name, SftpOperation.MKDIR) ?: return
        val dir = _state.value.path
        mutate(SftpOperation.MKDIR) { it.mkdir(join(dir, clean)) }
    }

    fun rename(entry: SftpEntry, newName: String) {
        val clean = validateName(newName, SftpOperation.RENAME) ?: return
        val target = join(parentOf(entry.path), clean)
        if (target == entry.path) return
        mutate(SftpOperation.RENAME) { it.rename(entry.path, target) }
    }

    fun delete(entry: SftpEntry) {
        mutate(SftpOperation.DELETE) { it.remove(entry.path, recursive = entry.isDirectory) }
    }

    fun chmod(entry: SftpEntry, mode: Int) {
        if (mode !in 0..PERMISSION_MASK) {
            reportMessage(SftpOperation.CHMOD, "Permissions must be between 000 and 777")
            return
        }
        mutate(SftpOperation.CHMOD) { it.chmod(entry.path, mode) }
    }

    private fun mutate(operation: SftpOperation, block: suspend (SshSftp) -> Unit) {
        val client = sftp ?: return
        scope.launch {
            try {
                directoryMutex.withLock { block(client) }
                refreshInternal()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                report(operation, t)
            }
        }
    }

    // --------------------------------------------------------------- transfers

    fun upload(name: String, source: InputStream, size: Long?) {
        val conn = connection
        if (conn == null) {
            runCatching { source.close() }
            reportMessage(SftpOperation.UPLOAD, "Not connected")
            return
        }
        val target = join(_state.value.path, name)
        val id = UUID.randomUUID().toString()
        addTransfer(SftpTransfer(id, name, SftpTransferDirection.UPLOAD, 0L, size))
        scope.launch(Dispatchers.IO) {
            var client: SshSftp? = null
            try {
                client = conn.openSftp()
                var reported = 0L
                client.upload(target, source, onProgress = { done ->
                    if (done - reported >= PROGRESS_STEP_BYTES) {
                        reported = done
                        updateTransfer(id) { it.copy(bytesDone = done) }
                    }
                })
                updateTransfer(id) {
                    it.copy(status = SftpTransferStatus.DONE, bytesDone = size ?: it.bytesDone)
                }
                refreshInternal()
            } catch (e: CancellationException) {
                updateTransfer(id) { it.copy(status = SftpTransferStatus.FAILED, error = "Cancelled") }
                throw e
            } catch (t: Throwable) {
                updateTransfer(id) { it.copy(status = SftpTransferStatus.FAILED, error = t.message) }
                report(SftpOperation.UPLOAD, t)
            } finally {
                runCatching { source.close() }
                runCatching { client?.close() }
            }
        }
    }

    fun download(entry: SftpEntry, sink: OutputStream) {
        val conn = connection
        if (conn == null) {
            runCatching { sink.close() }
            reportMessage(SftpOperation.DOWNLOAD, "Not connected")
            return
        }
        val id = UUID.randomUUID().toString()
        addTransfer(
            SftpTransfer(
                id = id,
                name = entry.name,
                direction = SftpTransferDirection.DOWNLOAD,
                bytesTotal = entry.size,
            ),
        )
        scope.launch(Dispatchers.IO) {
            var client: SshSftp? = null
            try {
                client = conn.openSftp()
                var reported = 0L
                client.download(entry.path, sink, onProgress = { done ->
                    if (done - reported >= PROGRESS_STEP_BYTES) {
                        reported = done
                        updateTransfer(id) { it.copy(bytesDone = done) }
                    }
                })
                sink.flush()
                updateTransfer(id) {
                    it.copy(status = SftpTransferStatus.DONE, bytesDone = entry.size)
                }
            } catch (e: CancellationException) {
                updateTransfer(id) { it.copy(status = SftpTransferStatus.FAILED, error = "Cancelled") }
                throw e
            } catch (t: Throwable) {
                updateTransfer(id) { it.copy(status = SftpTransferStatus.FAILED, error = t.message) }
                report(SftpOperation.DOWNLOAD, t)
            } finally {
                runCatching { sink.close() }
                runCatching { client?.close() }
            }
        }
    }

    fun clearOperationError() {
        _state.update { it.copy(operationError = null) }
    }

    fun close() {
        disconnect()
        connectJob = null
    }

    // -------------------------------------------------------------- connection

    private suspend fun connectInternal(serverId: Long) {
        hostKeyRejected = false
        hostKeyTimedOut = false
        _state.value = SftpState(serverId = serverId, status = SftpStatus.CONNECTING)
        try {
            val server = serverRepository.getServer(serverId)
                ?: throw SshException(SshErrorKind.UNKNOWN, "This server no longer exists.")
            val credentials = serverRepository.credentials(serverId)
                ?: throw SshException(
                    SshErrorKind.AUTH,
                    "No saved credentials. Edit the server and enter them again.",
                )
            val jumpHost = server.jumpServerId?.let { jumpId ->
                val jumpServer = serverRepository.getServer(jumpId)
                    ?: throw SshException(
                        SshErrorKind.UNKNOWN,
                        "The selected jump server no longer exists.",
                    )
                val jumpCredentials = serverRepository.credentials(jumpId)
                    ?: throw SshException(
                        SshErrorKind.AUTH,
                        "The selected jump server has no saved credentials.",
                    )
                SshHopConfig(
                    host = jumpServer.host,
                    port = jumpServer.port,
                    username = jumpServer.username,
                    credentials = jumpCredentials,
                    verifier = hostKeyVerifier(jumpServer.host, jumpServer.port),
                )
            }

            _state.update {
                it.copy(
                    serverName = server.name,
                    hostLabel = "${server.username}@${server.host}:${server.port}",
                )
            }

            val config = SshConnectionConfig(
                host = server.host,
                port = server.port,
                username = server.username,
                credentials = credentials,
                jumpHost = jumpHost,
            )
            val conn = engine.connect(config, hostKeyVerifier(server.host, server.port))
            connection = conn
            val client = conn.openSftp()
            sftp = client
            val home = runCatching { client.canonicalize(".") }.getOrDefault("/")
            _state.update {
                it.copy(status = SftpStatus.CONNECTED, error = null, path = home, loading = true)
            }
            loadDirectory(home)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            closeTransport()
            val error = when {
                hostKeyRejected ->
                    if (hostKeyTimedOut) SessionError.HOST_KEY_TIMEOUT else SessionError.HOST_KEY_REJECTED

                else -> sessionError(t)
            }
            _state.update {
                it.copy(
                    status = SftpStatus.DISCONNECTED,
                    error = error,
                    hostKeyPrompt = null,
                    loading = false,
                )
            }
        }
    }

    private suspend fun loadDirectory(path: String) {
        val client = sftp
        if (client == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        try {
            val entries = directoryMutex.withLock { client.list(path) }
            _state.update { it.copy(path = path, entries = entries, loading = false, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _state.update { it.copy(loading = false) }
            report(SftpOperation.LIST, t)
        }
    }

    private suspend fun refreshInternal() {
        val client = sftp ?: return
        val path = _state.value.path
        try {
            val entries = directoryMutex.withLock { client.list(path) }
            _state.update { it.copy(entries = entries) }
        } catch (_: Throwable) {
            // A failed refresh after a successful operation is not fatal.
        }
    }

    private suspend fun hostKeyVerifier(host: String, port: Int) = KnownHostsHostKeyVerifier(
        host = host,
        port = port,
        knownKeys = knownHostRepository.keysFor(host, port),
        prompt = { challenge -> promptHostKeyBlocking(challenge) },
    )

    private fun promptHostKeyBlocking(challenge: HostKeyChallenge): Boolean {
        if (userDisconnected) return false
        val pending = PendingPrompt(challenge, CountDownLatch(1), AtomicBoolean(false))
        pendingPrompt = pending
        _state.update {
            it.copy(status = SftpStatus.VERIFYING_HOST_KEY, hostKeyPrompt = challenge)
        }
        val completed = try {
            pending.latch.await(HOST_KEY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            false
        }
        if (pendingPrompt === pending) pendingPrompt = null
        _state.update { it.copy(hostKeyPrompt = null) }
        if (!completed) {
            hostKeyTimedOut = true
            hostKeyRejected = true
            return false
        }
        val accepted = pending.result.get()
        if (accepted) {
            _state.update { it.copy(status = SftpStatus.CONNECTING) }
        } else {
            hostKeyRejected = true
        }
        return accepted
    }

    private fun releasePendingPrompt() {
        pendingPrompt?.let { pending ->
            pending.result.set(false)
            pending.latch.countDown()
        }
        pendingPrompt = null
    }

    private fun closeTransport() {
        val client = sftp
        val conn = connection
        sftp = null
        connection = null
        if (client != null || conn != null) {
            scope.launch(Dispatchers.IO) {
                runCatching { client?.close() }
                runCatching { conn?.close() }
            }
        }
    }

    // ----------------------------------------------------------------- helpers

    private fun addTransfer(transfer: SftpTransfer) {
        _state.update { it.copy(transfers = (listOf(transfer) + it.transfers).take(MAX_TRANSFERS)) }
    }

    private fun updateTransfer(id: String, transform: (SftpTransfer) -> SftpTransfer) {
        _state.update { state ->
            state.copy(transfers = state.transfers.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun report(operation: SftpOperation, t: Throwable) {
        reportMessage(operation, t.message)
    }

    private fun reportMessage(operation: SftpOperation, detail: String?) {
        _state.update {
            it.copy(operationError = SftpOperationError(errorIds.incrementAndGet(), operation, detail))
        }
    }

    private fun validateName(name: String, operation: SftpOperation): String? {
        val clean = name.trim()
        if (clean.isEmpty() || clean == "." || clean == ".." || clean.contains('/')) {
            reportMessage(operation, "A file name cannot be empty or contain '/'")
            return null
        }
        return clean
    }

    private fun sessionError(t: Throwable): SessionError = when (t) {
        is SshException -> when (t.kind) {
            SshErrorKind.DNS -> SessionError.HOST_NOT_FOUND
            SshErrorKind.TIMEOUT -> SessionError.CONNECT_TIMEOUT
            SshErrorKind.REFUSED -> SessionError.CONNECTION_REFUSED
            SshErrorKind.AUTH -> SessionError.AUTH_FAILED
            SshErrorKind.HOST_KEY -> SessionError.HOST_KEY_REJECTED
            SshErrorKind.KEY_FORMAT -> SessionError.KEY_FORMAT
            SshErrorKind.PROTOCOL -> SessionError.PROTOCOL
            SshErrorKind.NETWORK -> SessionError.NETWORK
            SshErrorKind.UNKNOWN -> SessionError.UNKNOWN
        }

        is CredentialsUnavailableException -> SessionError.CREDENTIALS_UNAVAILABLE
        else -> SessionError.UNKNOWN
    }

    private fun parentOf(path: String): String {
        val normalized = path.trimEnd('/')
        if (normalized.isEmpty()) return "/"
        val index = normalized.lastIndexOf('/')
        return if (index <= 0) "/" else normalized.substring(0, index)
    }

    private fun join(directory: String, name: String): String =
        if (directory == "/") "/$name" else "${directory.trimEnd('/')}/$name"

    private companion object {
        const val HOST_KEY_TIMEOUT_SECONDS = 180L
        const val MAX_TRANSFERS = 20
        const val PROGRESS_STEP_BYTES = 256L * 1024L
        const val PERMISSION_MASK = 0x1FF
    }
}
