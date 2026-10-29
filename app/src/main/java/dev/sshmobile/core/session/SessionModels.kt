package dev.sshmobile.core.session

import dev.sshmobile.core.terminal.TerminalSnapshot
import dev.sshmobile.domain.model.HostKeyChallenge
import kotlinx.coroutines.flow.StateFlow

enum class SessionStatus {
    CONNECTING,
    VERIFYING_HOST_KEY,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    CLOSED,
}

/** Platform-neutral error codes, localized by the UI. */
enum class SessionError {
    SESSION_UNAVAILABLE,
    SERVER_DELETED,
    NO_CREDENTIALS,
    CREDENTIALS_UNAVAILABLE,
    HOST_KEY_REJECTED,
    HOST_KEY_TIMEOUT,
    AUTH_FAILED,
    HOST_NOT_FOUND,
    CONNECT_TIMEOUT,
    CONNECTION_REFUSED,
    NETWORK,
    KEY_FORMAT,
    PROTOCOL,
    SESSION_ENDED,
    CONNECTION_LOST,
    AUTO_RECONNECT_DISABLED,
    UNKNOWN,
}

data class TerminalSessionState(
    val id: String,
    val serverId: Long,
    val serverName: String = "SSH session",
    val hostLabel: String = "",
    val status: SessionStatus = SessionStatus.CONNECTING,
    val error: SessionError? = null,
    val hostKeyPrompt: HostKeyChallenge? = null,
    val connectedAt: Long? = null,
    val reconnectAttempt: Int = 0,
    val autoReconnect: Boolean = true,
    val terminalTitle: String = "",
)

/** Lightweight description used by the Sessions screen. */
data class SessionInfo(
    val id: String,
    val serverId: Long,
    val serverName: String,
    val hostLabel: String,
    val status: SessionStatus,
    val connectedAt: Long?,
    val terminalTitle: String,
)

fun TerminalSessionState.toInfo(): SessionInfo = SessionInfo(
    id = id,
    serverId = serverId,
    serverName = serverName,
    hostLabel = hostLabel,
    status = status,
    connectedAt = connectedAt,
    terminalTitle = terminalTitle,
)

/** Read-only view of a live terminal session, consumed by the UI. */
interface TerminalSessionHandle {
    val id: String
    val state: StateFlow<TerminalSessionState>
    val snapshot: StateFlow<TerminalSnapshot>

    fun send(data: ByteArray)
    fun sendText(text: String)
    fun resize(cols: Int, rows: Int)
    fun scrollBy(lines: Int)
    fun scrollToBottom()
    fun dumpText(): String
    fun textForLines(startLine: Int, endLine: Int): String
    fun respondToHostKey(challengeId: String, accept: Boolean)
    fun reconnectNow()
    fun close()
}
