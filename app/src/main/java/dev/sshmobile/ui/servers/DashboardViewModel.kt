package dev.sshmobile.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sshmobile.core.session.SessionInfo
import dev.sshmobile.core.session.SshSessionManager
import dev.sshmobile.data.repository.ServerRepository
import dev.sshmobile.domain.model.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ServerGroup(val name: String?, val servers: List<Server>)

data class DashboardUiState(
    val favorites: List<Server> = emptyList(),
    val groups: List<ServerGroup> = emptyList(),
    val activeSessions: Map<Long, SessionInfo> = emptyMap(),
    val totalServers: Int = 0,
    val query: String = "",
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = totalServers == 0
    val noResults: Boolean get() = totalServers > 0 && favorites.isEmpty() && groups.all { it.servers.isEmpty() }
}

class DashboardViewModel(
    private val repository: ServerRepository,
    private val sessionManager: SshSessionManager,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<DashboardUiState> = combine(
        repository.observeServers(),
        query,
        sessionManager.sessions,
    ) { servers, search, sessions ->
        val visible = if (search.isBlank()) {
            servers
        } else {
            val needle = search.trim().lowercase()
            servers.filter { server ->
                server.name.lowercase().contains(needle) ||
                    server.host.lowercase().contains(needle) ||
                    server.username.lowercase().contains(needle) ||
                    server.groupName?.lowercase()?.contains(needle) == true
            }
        }
        val favorites = visible.filter { it.favorite }
        val grouped = visible.filterNot { it.favorite }
            .groupBy { it.groupName }
            .toList()
            .sortedWith(
                compareBy<Pair<String?, List<Server>>> { if (it.first == null) 1 else 0 }
                    .thenBy { it.first?.lowercase() ?: "" },
            )
            .map { ServerGroup(it.first, it.second) }
        DashboardUiState(
            favorites = favorites,
            groups = grouped,
            activeSessions = sessions.associateBy { it.serverId },
            totalServers = servers.size,
            query = search,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun openSession(serverId: Long): String = sessionManager.openSession(serverId)

    fun toggleFavorite(server: Server) {
        viewModelScope.launch { repository.setFavorite(server.id, !server.favorite) }
    }

    fun delete(server: Server) {
        viewModelScope.launch { repository.delete(server.id) }
    }
}
