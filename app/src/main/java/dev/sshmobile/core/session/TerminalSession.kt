package dev.sshmobile.core.session

import dev.sshmobile.core.ssh.KnownHostsHostKeyVerifier
import dev.sshmobile.core.ssh.SshConnection
import dev.sshmobile.core.ssh.SshConnectionConfig
import dev.sshmobile.core.ssh.SshEngine
import dev.sshmobile.core.ssh.SshException
import dev.sshmobile.core.ssh.SshHopConfig
import dev.sshmobile.core.ssh.PortForwardSpec
import dev.sshmobile.core.ssh.SshShell
import dev.sshmobile.core.ssh.SshPortForward
import dev.sshmobile.core.terminal.TerminalEmulator
import dev.sshmobile.core.terminal.TerminalSnapshot
import dev.sshmobile.data.repository.CredentialsUnavailableException
import dev.sshmobile.data.repository.KnownHostRepository
import dev.sshmobile.data.repository.ServerRepository
import dev.sshmobile.domain.model.HostKeyChallenge
import dev.sshmobile.domain.model.KnownHostKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel as KTChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One live SSH session: owns the transport, the PTY shell, the terminal buffer and the reconnect
 * state machine. UI code only sees [TerminalSessionHandle].
 *
 * Lifecycle:
 *  - [start] launches the connection loop;
 *  - the loop reconnects with exponential backoff after an unexpected transport drop;
 *  - a clean shell exit (user typed `exit`) never reconnects;
 *  - [close] disposes the transport, cancels all coroutines and completes a pending host-key prompt.
 */
internal class TerminalSession(
    override val id: String,
    private val serverId: Long,
    private val engine: SshEngine,
    private val serverRepository: ServerRepository,
    private val knownHostRepository: KnownHostRepository,
    private val scope: CoroutineScope,
    private val autoReconnectEnabled: () -> Boolean,
    private val clock: () -> Long,
    private val onClosed: (TerminalSession) -> Unit,
    scrollbackLines: Int = 5000,
) : TerminalSessionHandle {

    private data class PendingPrompt(
        val challenge: HostKeyChallenge,
        val latch: CountDownLatch,
        val result: AtomicBoolean,
    )

    private sealed interface ShellRequest {
        data class Write(val data: ByteArray) : ShellRequest
        data class Resize(val cols: Int, val rows: Int) : ShellRequest
    }

    private data class ManagedPortForward(
        val info: ActivePortForward,
        val handle: SshPortForward,
    )

    private val emulator = TerminalEmulator(cols = 80, rows = 24, maxScrollback = scrollbackLines)

    private val _state = MutableStateFlow(
        TerminalSessionState(id = id, serverId = serverId),
    )
    override val state: StateFlow<TerminalSessionState> = _state.asStateFlow()

    private val forwardLock = Any()
    private val managedPortForwards = linkedMapOf<String, ManagedPortForward>()
    private val _portForwards = MutableStateFlow<List<ActivePortForward>>(emptyList())
    override val portForwards: StateFlow<List<ActivePortForward>> = _portForwards.asStateFlow()

    private val _snapshot = MutableStateFlow(emulator.snapshot())
    override val snapshot: StateFlow<TerminalSnapshot> = _snapshot.asStateFlow()

    private val snapshotSignal = KTChannel<Unit>(KTChannel.CONFLATED)
    private val publisherJob = scope.launch {
        for (ignored in snapshotSignal) {
            delay(SNAPSHOT_INTERVAL_MS)
            publishSnapshot()
        }
    }

    private var runJob: Job? = null
    private var writerJob: Job? = null

    @Volatile
    private var activeConnection: SshConnection? = null

    @Volatile
    private var activeShell: SshShell? = null

    @Volatile
    private var writeChannel: KTChannel<ShellRequest>? = null

    @Volatile
    private var pendingPrompt: PendingPrompt? = null

    @Volatile
    private var userClosed = false

    @Volatile
    private var hostKeyRejected = false

    @Volatile
    private var hostKeyTimedOut = false

    private val disposed = AtomicBoolean(false)

    init {
        emulator.onResponse = { bytes -> enqueueWrite(bytes) }
    }

    fun start() {
        if (runJob?.isActive == true || userClosed) return
        runJob = scope.launch { runSession() }
    }

    private suspend fun runSession() {
        var attempt = 0
        while (currentCoroutineContext().isActive && !userClosed) {
            if (attempt > 0) {
                val enabled = autoReconnectEnabled()
                _state.update {
                    it.copy(
                        status = SessionStatus.RECONNECTING,
                        reconnectAttempt = attempt,
                        autoReconnect = enabled,
                    )
                }
                if (!enabled) {
                    markDisconnected(SessionError.AUTO_RECONNECT_DISABLED)
                    return
                }
                delay(backoffMillis(attempt))
            }

            try {
                val cleanExit = connectAndRunShell()
                closeConnection()
                if (userClosed) return
                if (cleanExit) {
                    markDisconnected(SessionError.SESSION_ENDED)
                    return
                }
                if (!autoReconnectEnabled()) {
                    markDisconnected(SessionError.AUTO_RECONNECT_DISABLED)
                    return
                }
                attempt = 1
                _state.update {
                    it.copy(
                        status = SessionStatus.RECONNECTING,
                        reconnectAttempt = attempt,
                        error = SessionError.CONNECTION_LOST,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                closeConnection()
                if (userClosed) return
                val error = sessionError(t)
                if (hostKeyRejected) {
                    markDisconnected(
                        if (hostKeyTimedOut) SessionError.HOST_KEY_TIMEOUT
                        else SessionError.HOST_KEY_REJECTED,
                    )
                    return
                }
                if (t is SshException && t.kind == dev.sshmobile.core.ssh.SshErrorKind.AUTH) {
                    // Retrying with the same bad credentials only locks accounts.
                    markDisconnected(error)
                    return
                }
                if (!autoReconnectEnabled() || attempt >= MAX_RECONNECT_ATTEMPTS) {
                    markDisconnected(error)
                    return
                }
                attempt = if (attempt <= 0) 1 else attempt + 1
                _state.update {
                    it.copy(
                        status = SessionStatus.RECONNECTING,
                        reconnectAttempt = attempt,
                        error = error,
                    )
                }
            }
        }
    }

    /** @return true when the shell exited cleanly (remote `exit`), false on transport failure. */
    private suspend fun connectAndRunShell(): Boolean {
        hostKeyRejected = false
        hostKeyTimedOut = false
        _state.update {
            it.copy(
                status = SessionStatus.CONNECTING,
                error = null,
                hostKeyPrompt = null,
                reconnectAttempt = 0,
                autoReconnect = autoReconnectEnabled(),
            )
        }

        val server = serverRepository.getServer(serverId)
            ?: throw SshException(
                dev.sshmobile.core.ssh.SshErrorKind.UNKNOWN,
                "This server no longer exists.",
            )
        val credentials = serverRepository.credentials(serverId)
            ?: throw SshException(
                dev.sshmobile.core.ssh.SshErrorKind.AUTH,
                "No saved credentials. Edit the server and enter them again.",
            )
        val verifier = hostKeyVerifier(server.host, server.port)
        val jumpHost = server.jumpServerId?.let { jumpServerId ->
            val jumpServer = serverRepository.getServer(jumpServerId)
                ?: throw SshException(
                    dev.sshmobile.core.ssh.SshErrorKind.UNKNOWN,
                    "The selected jump server no longer exists.",
                )
            val jumpCredentials = serverRepository.credentials(jumpServerId)
                ?: throw SshException(
                    dev.sshmobile.core.ssh.SshErrorKind.AUTH,
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
        val connection = engine.connect(config, verifier)
        activeConnection = connection

        val size = emulator.size
        val shell = connection.openShell(size.cols, size.rows)
        activeShell = shell

        val channel = KTChannel<ShellRequest>(KTChannel.UNLIMITED)
        writeChannel = channel
        writerJob = scope.launch(Dispatchers.IO) { writeLoop(shell, channel) }

        _state.update {
            it.copy(
                status = SessionStatus.CONNECTED,
                connectedAt = clock(),
                error = null,
                hostKeyPrompt = null,
                terminalTitle = emulator.osTitle,
            )
        }
        publishSnapshot()

        val cleanExit = pumpOutput(shell)
        return cleanExit && connection.isConnected()
    }

    private suspend fun hostKeyVerifier(host: String, port: Int) = KnownHostsHostKeyVerifier(
        host = host,
        port = port,
        knownKeys = knownHostRepository.keysFor(host, port),
        prompt = { challenge -> promptHostKeyBlocking(challenge) },
    )

    private suspend fun pumpOutput(shell: SshShell): Boolean = withContext(Dispatchers.IO) {
        val buffer = ByteArray(8192)
        try {
            while (currentCoroutineContext().isActive) {
                val read = shell.input.read(buffer)
                if (read < 0) return@withContext true
                if (read > 0) {
                    emulator.feed(buffer, 0, read)
                    snapshotSignal.trySend(Unit)
                }
            }
            false
        } catch (_: IOException) {
            false
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun writeLoop(shell: SshShell, channel: KTChannel<ShellRequest>) {
        try {
            for (request in channel) {
                when (request) {
                    is ShellRequest.Write -> {
                        shell.output.write(request.data)
                        shell.output.flush()
                    }
                    is ShellRequest.Resize -> shell.resize(request.cols, request.rows)
                }
            }
        } catch (_: IOException) {
            // The read loop notices the closed channel and triggers reconnect logic.
        } catch (_: Throwable) {
        } finally {
            runCatching { shell.close() }
        }
    }

    // ------------------------------------------------------------ host key UI

    private fun promptHostKeyBlocking(challenge: HostKeyChallenge): Boolean {
        if (userClosed) return false
        val pending = PendingPrompt(challenge, CountDownLatch(1), AtomicBoolean(false))
        pendingPrompt = pending
        _state.update {
            it.copy(status = SessionStatus.VERIFYING_HOST_KEY, hostKeyPrompt = challenge)
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
            _state.update { it.copy(status = SessionStatus.CONNECTING) }
        } else {
            hostKeyRejected = true
        }
        return accepted
    }

    override fun respondToHostKey(challengeId: String, accept: Boolean) {
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

    // --------------------------------------------------------------- terminal

    override fun send(data: ByteArray) {
        if (userClosed || data.isEmpty()) return
        emulator.scrollToBottom()
        enqueueWrite(data)
        snapshotSignal.trySend(Unit)
    }

    override fun sendText(text: String) {
        if (text.isEmpty()) return
        send(text.toByteArray(StandardCharsets.UTF_8))
    }

    private fun enqueueWrite(data: ByteArray) {
        val channel = writeChannel ?: return
        channel.trySend(ShellRequest.Write(data.copyOf()))
    }

    override fun resize(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        val current = emulator.size
        if (current.cols == cols && current.rows == rows) return
        emulator.resize(cols, rows)
        _snapshot.value = emulator.snapshot()
        writeChannel?.trySend(ShellRequest.Resize(cols, rows))
    }

    override fun scrollBy(lines: Int) {
        emulator.scrollBy(lines)
        publishSnapshot()
    }

    override fun scrollToBottom() {
        emulator.scrollToBottom()
        publishSnapshot()
    }

    override fun dumpText(): String = emulator.dumpText()

    override fun textForLines(startLine: Int, endLine: Int): String =
        emulator.textForLines(startLine, endLine)

    override suspend fun startPortForward(spec: PortForwardSpec): ActivePortForward {
        val connection = activeConnection ?: throw IllegalStateException("SSH session is not connected")
        val handle = connection.startPortForward(spec)
        val info = ActivePortForward(
            id = java.util.UUID.randomUUID().toString(),
            spec = spec,
            boundPort = handle.boundPort,
        )
        val registered = synchronized(forwardLock) {
            if (userClosed || activeConnection !== connection) {
                false
            } else {
                managedPortForwards[info.id] = ManagedPortForward(info, handle)
                _portForwards.value = managedPortForwards.values.map { it.info }
                true
            }
        }
        if (!registered) {
            handle.close()
            throw IllegalStateException("SSH connection changed while starting the port forward")
        }
        return info
    }

    override fun stopPortForward(id: String) {
        val managed = synchronized(forwardLock) {
            val removed = managedPortForwards.remove(id)
            _portForwards.value = managedPortForwards.values.map { it.info }
            removed
        }
        managed?.handle?.close()
    }

    override fun reconnectNow() {
        if (userClosed) return
        if (runJob?.isActive == true) return
        _state.update {
            it.copy(status = SessionStatus.CONNECTING, error = null, reconnectAttempt = 0)
        }
        start()
    }

    override fun close() {
        if (userClosed) return
        userClosed = true
        pendingPrompt?.let { pending ->
            pending.result.set(false)
            pending.latch.countDown()
        }
        pendingPrompt = null
        runJob?.cancel()
        publisherJob.cancel()
        snapshotSignal.close()
        scope.launch(Dispatchers.IO) { closeConnection() }
        _state.update { it.copy(status = SessionStatus.CLOSED, hostKeyPrompt = null) }
        if (disposed.compareAndSet(false, true)) {
            onClosed(this)
        }
    }

    private fun closeConnection() {
        val connection = activeConnection
        activeConnection = null
        closePortForwards()
        runCatching { writeChannel?.close() }
        writeChannel = null
        writerJob?.cancel()
        writerJob = null
        runCatching { activeShell?.close() }
        activeShell = null
        runCatching { connection?.close() }
    }

    private fun closePortForwards() {
        val forwards = synchronized(forwardLock) {
            managedPortForwards.values.toList().also {
                managedPortForwards.clear()
                _portForwards.value = emptyList()
            }
        }
        forwards.forEach { runCatching { it.handle.close() } }
    }

    private fun markDisconnected(error: SessionError) {
        _state.update {
            it.copy(
                status = SessionStatus.DISCONNECTED,
                error = error,
                hostKeyPrompt = null,
            )
        }
    }

    private fun publishSnapshot() {
        val snapshot = emulator.snapshot()
        _snapshot.value = snapshot
        if (snapshot.title != _state.value.terminalTitle) {
            _state.update { it.copy(terminalTitle = snapshot.title) }
        }
    }

    private fun sessionError(t: Throwable): SessionError = when (t) {
        is SshException -> when (t.kind) {
            dev.sshmobile.core.ssh.SshErrorKind.DNS -> SessionError.HOST_NOT_FOUND
            dev.sshmobile.core.ssh.SshErrorKind.TIMEOUT -> SessionError.CONNECT_TIMEOUT
            dev.sshmobile.core.ssh.SshErrorKind.REFUSED -> SessionError.CONNECTION_REFUSED
            dev.sshmobile.core.ssh.SshErrorKind.AUTH -> SessionError.AUTH_FAILED
            dev.sshmobile.core.ssh.SshErrorKind.HOST_KEY -> SessionError.HOST_KEY_REJECTED
            dev.sshmobile.core.ssh.SshErrorKind.KEY_FORMAT -> SessionError.KEY_FORMAT
            dev.sshmobile.core.ssh.SshErrorKind.PROTOCOL -> SessionError.PROTOCOL
            dev.sshmobile.core.ssh.SshErrorKind.NETWORK -> SessionError.NETWORK
            dev.sshmobile.core.ssh.SshErrorKind.UNKNOWN -> SessionError.UNKNOWN
        }
        is CredentialsUnavailableException -> SessionError.CREDENTIALS_UNAVAILABLE
        else -> SessionError.UNKNOWN
    }

    private fun backoffMillis(attempt: Int): Long {
        val seconds = when (attempt) {
            1 -> 1L
            2 -> 2L
            3 -> 4L
            4 -> 8L
            else -> 15L
        }
        return seconds * 1000L
    }

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val SNAPSHOT_INTERVAL_MS = 16L
        private const val HOST_KEY_TIMEOUT_SECONDS = 180L
    }
}
