package dev.sshmobile.ui.sessions

import androidx.lifecycle.ViewModel
import dev.sshmobile.core.session.SessionInfo
import dev.sshmobile.core.session.SshSessionManager
import kotlinx.coroutines.flow.StateFlow

class SessionsViewModel(
    private val sessionManager: SshSessionManager,
) : ViewModel() {

    val sessions: StateFlow<List<SessionInfo>> = sessionManager.sessions

    fun closeSession(id: String) = sessionManager.closeSession(id)

    fun closeAll() = sessionManager.closeAll()
}
