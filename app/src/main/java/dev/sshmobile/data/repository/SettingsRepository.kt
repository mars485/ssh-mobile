package dev.sshmobile.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { DARK, LIGHT }

enum class AppLanguage { SYSTEM, ENGLISH, RUSSIAN }

data class AppSettings(
    val fontSizeSp: Float = 14f,
    val keepScreenOn: Boolean = true,
    val blockScreenshots: Boolean = false,
    val autoReconnect: Boolean = true,
    val scrollbackLines: Int = 5000,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.SYSTEM,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val fontSize = floatPreferencesKey("font_size_sp")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val blockScreenshots = booleanPreferencesKey("block_screenshots")
        val autoReconnect = booleanPreferencesKey("auto_reconnect")
        val scrollback = intPreferencesKey("scrollback_lines")
        val theme = stringPreferencesKey("theme_mode")
        val language = stringPreferencesKey("app_language")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            fontSizeSp = (prefs[Keys.fontSize] ?: 14f).coerceIn(10f, 24f),
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            blockScreenshots = prefs[Keys.blockScreenshots] ?: false,
            autoReconnect = prefs[Keys.autoReconnect] ?: true,
            scrollbackLines = prefs[Keys.scrollback] ?: 5000,
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.theme] ?: "DARK") }
                .getOrDefault(ThemeMode.DARK),
            language = runCatching { AppLanguage.valueOf(prefs[Keys.language] ?: "SYSTEM") }
                .getOrDefault(AppLanguage.SYSTEM),
        )
    }

    suspend fun setFontSize(value: Float) {
        context.settingsDataStore.edit {
            it[Keys.fontSize] = value.coerceIn(10f, 24f)
        }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.keepScreenOn] = value }
    }

    suspend fun setBlockScreenshots(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.blockScreenshots] = value }
    }

    suspend fun setAutoReconnect(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoReconnect] = value }
    }

    suspend fun setScrollbackLines(value: Int) {
        context.settingsDataStore.edit { it[Keys.scrollback] = value.coerceIn(500, 50000) }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.theme] = value.name }
    }

    suspend fun setLanguage(value: AppLanguage) {
        context.settingsDataStore.edit { it[Keys.language] = value.name }
    }
}
