package dev.sshmobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sshmobile.data.repository.AppSettings
import dev.sshmobile.data.repository.AppLanguage
import dev.sshmobile.data.repository.ConfigBackupRepository
import dev.sshmobile.data.repository.ConfigImportResult
import dev.sshmobile.data.repository.KnownHostRepository
import dev.sshmobile.data.repository.ServerRepository
import dev.sshmobile.data.repository.SettingsRepository
import dev.sshmobile.data.repository.TerminalCommandRepository
import dev.sshmobile.data.repository.TerminalFont
import dev.sshmobile.data.repository.TerminalColorScheme
import dev.sshmobile.data.repository.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val knownHostRepository: KnownHostRepository,
    private val serverRepository: ServerRepository,
    private val terminalCommandRepository: TerminalCommandRepository,
    private val configBackupRepository: ConfigBackupRepository,
    private val wipeKeystoreKey: () -> Unit,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    val knownHostCount: StateFlow<Int> = knownHostRepository.observeCount().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0,
    )

    fun setFontSize(value: Float) = viewModelScope.launch { settingsRepository.setFontSize(value) }

    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch { settingsRepository.setKeepScreenOn(value) }

    fun setBlockScreenshots(value: Boolean) =
        viewModelScope.launch { settingsRepository.setBlockScreenshots(value) }

    fun setAutoReconnect(value: Boolean) =
        viewModelScope.launch { settingsRepository.setAutoReconnect(value) }

    fun setScrollbackLines(value: Int) =
        viewModelScope.launch { settingsRepository.setScrollbackLines(value) }

    fun setTerminalFont(value: TerminalFont) =
        viewModelScope.launch { settingsRepository.setTerminalFont(value) }

    fun setTerminalColorScheme(value: TerminalColorScheme) =
        viewModelScope.launch { settingsRepository.setTerminalColorScheme(value) }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(value) }

    fun setLanguage(value: AppLanguage) = viewModelScope.launch { settingsRepository.setLanguage(value) }

    fun clearKnownHosts() = viewModelScope.launch { knownHostRepository.clearAll() }

    fun exportConfig(onReady: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onReady(runCatching { configBackupRepository.export() })
        }
    }

    fun importConfig(text: String, onDone: (Result<ConfigImportResult>) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching { configBackupRepository.import(text) })
        }
    }

    fun wipeAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                terminalCommandRepository.clearAll()
                serverRepository.clearAll()
                knownHostRepository.clearAll()
                wipeKeystoreKey()
            }
            onDone()
        }
    }
}
