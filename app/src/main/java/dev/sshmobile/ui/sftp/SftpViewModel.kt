package dev.sshmobile.ui.sftp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sshmobile.core.session.SftpSessionManager
import dev.sshmobile.core.session.SftpState
import dev.sshmobile.core.ssh.SftpEntry
import dev.sshmobile.data.repository.ServerRepository
import dev.sshmobile.domain.model.Server
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.InputStream
import java.io.OutputStream

class SftpViewModel(
    private val manager: SftpSessionManager,
    serverRepository: ServerRepository,
) : ViewModel() {

    val state: StateFlow<SftpState> = manager.state

    val servers: StateFlow<List<Server>> = serverRepository.observeServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun connect(serverId: Long) = manager.connect(serverId)

    fun disconnect() = manager.disconnect()

    fun open(path: String) = manager.open(path)

    fun refresh() = manager.refresh()

    fun up() = manager.up()

    fun mkdir(name: String) = manager.mkdir(name)

    fun rename(entry: SftpEntry, newName: String) = manager.rename(entry, newName)

    fun delete(entry: SftpEntry) = manager.delete(entry)

    fun chmod(entry: SftpEntry, mode: Int) = manager.chmod(entry, mode)

    fun upload(name: String, source: InputStream, size: Long?) = manager.upload(name, source, size)

    fun download(entry: SftpEntry, sink: OutputStream) = manager.download(entry, sink)

    fun respondToHostKey(challengeId: String, accept: Boolean) =
        manager.respondToHostKey(challengeId, accept)

    fun clearOperationError() = manager.clearOperationError()
}
