package dev.sshmobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sshmobile.data.repository.KnownHostRepository
import dev.sshmobile.domain.model.KnownHostKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class KnownHostsViewModel(
    private val repository: KnownHostRepository,
) : ViewModel() {

    val hosts: StateFlow<List<KnownHostKey>> = repository.observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun remove(key: KnownHostKey) {
        viewModelScope.launch { repository.remove(key.host, key.port, key.keyType) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }
}
