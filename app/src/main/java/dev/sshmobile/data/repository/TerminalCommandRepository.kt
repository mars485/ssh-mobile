package dev.sshmobile.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.sshmobile.core.security.SecretCipher
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TerminalCommandSnippet(
    val id: String,
    val name: String,
    val command: String,
)

data class TerminalCommandLibrary(
    val history: List<String> = emptyList(),
    val snippets: List<TerminalCommandSnippet> = emptyList(),
    val historyEnabled: Boolean = false,
)

private val Context.terminalCommandDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "terminal_commands",
)

/** Stores command history and snippets encrypted with the same Keystore-backed cipher as secrets. */
class TerminalCommandRepository(
    context: Context,
    private val cipher: SecretCipher,
) {
    private val dataStore = context.applicationContext.terminalCommandDataStore

    private object Keys {
        val history = stringPreferencesKey("history_encrypted")
        val snippets = stringPreferencesKey("snippets_encrypted")
        val historyEnabled = booleanPreferencesKey("history_enabled")
    }

    val library: Flow<TerminalCommandLibrary> = dataStore.data.map { preferences ->
        TerminalCommandLibrary(
            history = decodeHistory(preferences[Keys.history]),
            snippets = decodeSnippets(preferences[Keys.snippets]),
            historyEnabled = preferences[Keys.historyEnabled] ?: false,
        )
    }

    suspend fun recordCommand(command: String) {
        val normalized = command.trim()
        if (normalized.isEmpty()) return

        dataStore.edit { preferences ->
            if (preferences[Keys.historyEnabled] != true) return@edit
            val history = decodeHistory(preferences[Keys.history])
            if (history.lastOrNull() == normalized) return@edit
            preferences[Keys.history] = cipher.encrypt(
                TerminalCommandLibraryCodec.encodeHistory((history + normalized).takeLast(MAX_HISTORY)),
            )
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { it.remove(Keys.history) }
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.historyEnabled] = enabled }
    }

    suspend fun saveSnippet(
        name: String,
        command: String,
        id: String? = null,
    ): String {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Snippet name must not be blank" }
        require(command.isNotBlank()) { "Snippet command must not be blank" }

        val snippetId = id ?: UUID.randomUUID().toString()
        dataStore.edit { preferences ->
            val snippets = decodeSnippets(preferences[Keys.snippets]).toMutableList()
            val replacement = TerminalCommandSnippet(snippetId, normalizedName, command)
            val existingIndex = snippets.indexOfFirst { it.id == snippetId }
            if (existingIndex >= 0) {
                snippets[existingIndex] = replacement
            } else {
                snippets += replacement
            }
            preferences[Keys.snippets] = cipher.encrypt(
                TerminalCommandLibraryCodec.encodeSnippets(snippets.takeLast(MAX_SNIPPETS)),
            )
        }
        return snippetId
    }

    suspend fun deleteSnippet(id: String) {
        dataStore.edit { preferences ->
            val snippets = decodeSnippets(preferences[Keys.snippets]).filterNot { it.id == id }
            if (snippets.isEmpty()) {
                preferences.remove(Keys.snippets)
            } else {
                preferences[Keys.snippets] = cipher.encrypt(
                    TerminalCommandLibraryCodec.encodeSnippets(snippets),
                )
            }
        }
    }

    /** Clears ciphertext before the shared Keystore key is deleted by Settings. */
    suspend fun clearAll() {
        dataStore.edit {
            it.remove(Keys.history)
            it.remove(Keys.snippets)
            it.remove(Keys.historyEnabled)
        }
    }

    private fun decodeHistory(encrypted: String?): List<String> =
        decrypt(encrypted)?.let(TerminalCommandLibraryCodec::decodeHistory).orEmpty()

    private fun decodeSnippets(encrypted: String?): List<TerminalCommandSnippet> =
        decrypt(encrypted)?.let(TerminalCommandLibraryCodec::decodeSnippets).orEmpty()

    private fun decrypt(encrypted: String?): String? = encrypted?.let {
        runCatching { cipher.decrypt(it) }.getOrNull()
    }

    private companion object {
        const val MAX_HISTORY = 100
        const val MAX_SNIPPETS = 100
    }
}

/** Compact UTF-8/Base64 encoding keeps arbitrary multiline snippets unambiguous in preferences. */
internal object TerminalCommandLibraryCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encodeHistory(history: List<String>): String = history.joinToString("\n", transform = ::encode)

    fun decodeHistory(encoded: String): List<String> = decodeLines(encoded) { line ->
        decode(line)?.takeIf(String::isNotBlank)
    }

    fun encodeSnippets(snippets: List<TerminalCommandSnippet>): String =
        snippets.joinToString("\n") { snippet ->
            listOf(snippet.id, snippet.name, snippet.command).joinToString(".", transform = ::encode)
        }

    fun decodeSnippets(encoded: String): List<TerminalCommandSnippet> = decodeLines(encoded) { line ->
        val fields = line.split('.', limit = 3)
        if (fields.size != 3) return@decodeLines null
        val id = decode(fields[0]) ?: return@decodeLines null
        val name = decode(fields[1])?.takeIf(String::isNotBlank) ?: return@decodeLines null
        val command = decode(fields[2])?.takeIf(String::isNotBlank) ?: return@decodeLines null
        TerminalCommandSnippet(id = id, name = name, command = command)
    }

    private fun encode(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String? = runCatching {
        String(decoder.decode(value), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun <T : Any> decodeLines(encoded: String, decodeLine: (String) -> T?): List<T> =
        if (encoded.isEmpty()) emptyList() else encoded.lineSequence().mapNotNull(decodeLine).toList()
}
