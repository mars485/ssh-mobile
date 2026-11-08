package dev.sshmobile.core.session

import dev.sshmobile.core.ssh.SftpEntry
import dev.sshmobile.domain.model.HostKeyChallenge

enum class SftpStatus {
    IDLE,
    CONNECTING,
    VERIFYING_HOST_KEY,
    CONNECTED,
    DISCONNECTED,
}

enum class SftpTransferDirection { UPLOAD, DOWNLOAD }

enum class SftpTransferStatus { RUNNING, DONE, FAILED }

/** One queued or finished upload/download. Progress is reported in bytes. */
data class SftpTransfer(
    val id: String,
    val name: String,
    val direction: SftpTransferDirection,
    val bytesDone: Long = 0L,
    val bytesTotal: Long? = null,
    val status: SftpTransferStatus = SftpTransferStatus.RUNNING,
    val error: String? = null,
) {
    val progress: Float?
        get() = bytesTotal?.takeIf { it > 0L }?.let {
            (bytesDone.toFloat() / it.toFloat()).coerceIn(0f, 1f)
        }
}

/** Operation that failed; the UI localizes the label and shows [detail] as secondary text. */
enum class SftpOperation { LIST, MKDIR, RENAME, DELETE, CHMOD, UPLOAD, DOWNLOAD }

data class SftpOperationError(
    val id: Long,
    val operation: SftpOperation,
    val detail: String? = null,
)

data class SftpState(
    val serverId: Long? = null,
    val serverName: String = "",
    val hostLabel: String = "",
    val status: SftpStatus = SftpStatus.IDLE,
    val error: SessionError? = null,
    val hostKeyPrompt: HostKeyChallenge? = null,
    val path: String = "/",
    val entries: List<SftpEntry> = emptyList(),
    val loading: Boolean = false,
    val operationError: SftpOperationError? = null,
    val transfers: List<SftpTransfer> = emptyList(),
)
