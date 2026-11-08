package dev.sshmobile.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sshmobile.core.session.SessionError
import dev.sshmobile.core.session.SessionStatus
import dev.sshmobile.core.session.SshSessionManager
import dev.sshmobile.core.session.ActivePortForward
import dev.sshmobile.core.session.TerminalSessionHandle
import dev.sshmobile.core.session.TerminalSessionState
import dev.sshmobile.core.ssh.PortForwardSpec
import dev.sshmobile.core.terminal.TerminalInput
import dev.sshmobile.core.terminal.TerminalSnapshot
import dev.sshmobile.data.repository.TerminalCommandLibrary
import dev.sshmobile.data.repository.TerminalCommandRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TerminalViewModel(
    sessionManager: SshSessionManager,
    sessionId: String,
    private val commandRepository: TerminalCommandRepository,
) : ViewModel() {

    private val handle: TerminalSessionHandle? = sessionManager.getSession(sessionId)

    private val missingState = MutableStateFlow(
        TerminalSessionState(
            id = sessionId,
            serverId = 0,
            serverName = "Session unavailable",
            status = SessionStatus.CLOSED,
            error = SessionError.SESSION_UNAVAILABLE,
        ),
    )

    val state: StateFlow<TerminalSessionState> = handle?.state ?: missingState.asStateFlow()

    val snapshot: StateFlow<TerminalSnapshot> = handle?.snapshot
        ?: MutableStateFlow(TerminalSnapshot.empty()).asStateFlow()

    val portForwards: StateFlow<List<ActivePortForward>> = handle?.portForwards
        ?: MutableStateFlow<List<ActivePortForward>>(emptyList()).asStateFlow()

    val available: Boolean get() = handle != null

    val commandLibrary: StateFlow<TerminalCommandLibrary> = commandRepository.library.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TerminalCommandLibrary(),
    )

    fun send(data: ByteArray) {
        handle?.send(data)
    }

    fun sendText(text: String, ctrl: Boolean = false, alt: Boolean = false) {
        if (text.isEmpty()) return
        handle?.send(TerminalInput.encodeText(text, ctrl, alt))
    }

    fun recordCommand(command: String) {
        viewModelScope.launch { commandRepository.recordCommand(command) }
    }

    fun runCommand(command: String) {
        val normalized = command.trimEnd('\r', '\n')
        if (normalized.isBlank()) return
        handle?.send(TerminalInput.encodeText("$normalized\n"))
        recordCommand(normalized)
    }

    fun saveSnippet(name: String, command: String, id: String? = null) {
        viewModelScope.launch { commandRepository.saveSnippet(name, command, id) }
    }

    fun deleteSnippet(id: String) {
        viewModelScope.launch { commandRepository.deleteSnippet(id) }
    }

    fun clearCommandHistory() {
        viewModelScope.launch { commandRepository.clearHistory() }
    }

    fun setHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch { commandRepository.setHistoryEnabled(enabled) }
    }

    suspend fun startPortForward(spec: PortForwardSpec): ActivePortForward =
        handle?.startPortForward(spec) ?: error("Session unavailable")

    fun stopPortForward(id: String) {
        handle?.stopPortForward(id)
    }

    fun sendKey(bytes: ByteArray) {
        handle?.send(bytes)
    }

    fun paste(text: String, bracketed: Boolean) {
        handle?.send(TerminalInput.paste(text, bracketed))
    }

    fun resize(cols: Int, rows: Int) {
        handle?.resize(cols, rows)
    }

    fun scroll(lines: Int) {
        handle?.scrollBy(lines)
    }

    fun scrollToBottom() {
        handle?.scrollToBottom()
    }

    fun dumpText(): String = handle?.dumpText().orEmpty()

    fun textForLines(startLine: Int, endLine: Int): String =
        handle?.textForLines(startLine, endLine).orEmpty()

    fun respondToHostKey(challengeId: String, accept: Boolean) {
        handle?.respondToHostKey(challengeId, accept)
    }

    fun reconnect() {
        handle?.reconnectNow()
    }

    fun closeSession() {
        handle?.close()
    }
}
