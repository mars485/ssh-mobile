package dev.sshmobile.ui.terminal

import androidx.lifecycle.ViewModel
import dev.sshmobile.core.session.SessionError
import dev.sshmobile.core.session.SessionStatus
import dev.sshmobile.core.session.SshSessionManager
import dev.sshmobile.core.session.TerminalSessionHandle
import dev.sshmobile.core.session.TerminalSessionState
import dev.sshmobile.core.terminal.TerminalInput
import dev.sshmobile.core.terminal.TerminalSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TerminalViewModel(
    sessionManager: SshSessionManager,
    sessionId: String,
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

    val available: Boolean get() = handle != null

    fun send(data: ByteArray) {
        handle?.send(data)
    }

    fun sendText(text: String, ctrl: Boolean = false, alt: Boolean = false) {
        if (text.isEmpty()) return
        handle?.send(TerminalInput.encodeText(text, ctrl, alt))
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
