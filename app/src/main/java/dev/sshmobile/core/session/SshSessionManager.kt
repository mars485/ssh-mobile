package dev.sshmobile.core.session

import dev.sshmobile.core.ssh.SshEngine
import dev.sshmobile.data.repository.AppSettings
import dev.sshmobile.data.repository.KnownHostRepository
import dev.sshmobile.data.repository.ServerRepository
import dev.sshmobile.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Application scoped registry of SSH sessions. Sessions survive navigation and configuration
 * changes; they are only torn down explicitly, when the process dies, or on [closeAll].
 */
class SshSessionManager(
    private val engine: SshEngine,
    private val serverRepository: ServerRepository,
    private val knownHostRepository: KnownHostRepository,
    settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var settings = AppSettings()

    private val _runtimes = MutableStateFlow<List<TerminalSession>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessions: StateFlow<List<SessionInfo>> = _runtimes
        .flatMapLatest { runtimes ->
            if (runtimes.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(runtimes.map { it.state }) { states: Array<TerminalSessionState> ->
                    states.map { it.toInfo() }
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            settingsRepository.settings.collect { settings = it }
        }
    }

    fun openSession(serverId: Long): String {
        val id = UUID.randomUUID().toString()
        val session = TerminalSession(
            id = id,
            serverId = serverId,
            engine = engine,
            serverRepository = serverRepository,
            knownHostRepository = knownHostRepository,
            scope = scope,
            autoReconnectEnabled = { settings.autoReconnect },
            clock = clock,
            scrollbackLines = settings.scrollbackLines,
            onClosed = { closed ->
                _runtimes.update { list -> list.filterNot { it.id == closed.id } }
            },
        )
        _runtimes.update { listOf(session) + it }
        session.start()
        return id
    }

    fun getSession(id: String): TerminalSessionHandle? =
        _runtimes.value.firstOrNull { it.id == id }

    fun respondToHostKey(sessionId: String, challengeId: String, accept: Boolean) {
        getSession(sessionId)?.respondToHostKey(challengeId, accept)
    }

    fun closeSession(id: String) {
        val session = _runtimes.value.firstOrNull { it.id == id } ?: return
        session.close()
        _runtimes.update { list -> list.filterNot { it.id == id } }
    }

    fun closeAll() {
        _runtimes.value.toList().forEach { it.close() }
        _runtimes.value = emptyList()
    }
}
