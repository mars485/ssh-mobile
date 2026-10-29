package dev.sshmobile.ui.servers

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sshmobile.core.ssh.PrivateKeyInspector
import dev.sshmobile.data.repository.ServerRepository
import dev.sshmobile.domain.model.AuthType
import dev.sshmobile.domain.model.Server
import dev.sshmobile.domain.model.ServerDraft
import dev.sshmobile.domain.model.ServerValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

data class ServerEditUiState(
    val serverId: Long = 0,
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val originalAuthType: AuthType = AuthType.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val groupName: String = "",
    val favorite: Boolean = false,
    val hasStoredSecret: Boolean = false,
    val storedKeySummary: String? = null,
    val importedKeyName: String? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val validationErrors: List<String> = emptyList(),
    val saved: Boolean = false,
    val deleted: Boolean = false,
) {
    val isNew: Boolean get() = serverId == 0L
    val canKeepSecret: Boolean get() = hasStoredSecret && authType == originalAuthType
}

class ServerEditViewModel(
    private val serverId: Long,
    private val repository: ServerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerEditUiState(serverId = serverId, loading = serverId != 0L))
    val state: StateFlow<ServerEditUiState> = _state.asStateFlow()

    init {
        if (serverId != 0L) {
            viewModelScope.launch {
                val server = repository.getServer(serverId)
                if (server == null) {
                    _state.update {
                        it.copy(loading = false, error = "This server no longer exists.")
                    }
                } else {
                    _state.update { it.fromServer(server) }
                }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun setHost(value: String) = _state.update { it.copy(host = value, error = null) }
    fun setPort(value: String) = _state.update {
        it.copy(port = value.filter { ch -> ch.isDigit() }.take(5), error = null)
    }

    fun setUsername(value: String) = _state.update { it.copy(username = value, error = null) }

    fun setGroup(value: String) = _state.update { it.copy(groupName = value) }

    fun setFavorite(value: Boolean) = _state.update { it.copy(favorite = value) }

    fun setAuthType(type: AuthType) = _state.update {
        if (it.authType == type) it else it.copy(authType = type, error = null, validationErrors = emptyList())
    }

    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun setPrivateKey(value: String) = _state.update {
        it.copy(privateKey = value, importedKeyName = null, error = null)
    }

    fun setPassphrase(value: String) = _state.update { it.copy(passphrase = value, error = null) }

    fun importKey(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.applicationContext.contentResolver
                    val name = resolver.displayName(uri) ?: "private key"
                    val bytes = resolver.openInputStream(uri)?.use { stream ->
                        readLimited(stream, MAX_KEY_BYTES + 1)
                    } ?: throw IllegalStateException("The selected file could not be opened")
                    if (bytes.size > MAX_KEY_BYTES) {
                        throw IllegalStateException("The key file is too large")
                    }
                    val text = String(bytes, StandardCharsets.UTF_8)
                    if (!text.contains("PRIVATE KEY")) {
                        throw IllegalStateException("The selected file does not look like a private key")
                    }
                    name to text
                }
            }
            _state.update { current ->
                result.fold(
                    onSuccess = { (name, text) ->
                        current.copy(
                            privateKey = text,
                            importedKeyName = name,
                            authType = AuthType.PRIVATE_KEY,
                            error = null,
                            validationErrors = emptyList(),
                        )
                    },
                    onFailure = {
                        current.copy(error = it.message ?: "Could not read the selected key")
                    },
                )
            }
        }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val port = current.port.toIntOrNull() ?: 0
        val draft = ServerDraft(
            id = current.serverId,
            name = current.name,
            host = current.host,
            port = port,
            username = current.username,
            authType = current.authType,
            groupName = current.groupName.ifBlank { null },
            favorite = current.favorite,
            newSecret = when {
                current.authType == AuthType.PASSWORD -> current.password.takeIf { it.isNotEmpty() }
                current.privateKey.isNotBlank() -> current.privateKey
                else -> null
            },
            newPassphrase = when {
                current.authType != AuthType.PRIVATE_KEY -> null
                current.passphrase.isNotEmpty() -> current.passphrase
                current.privateKey.isNotBlank() -> "" // replacing the key resets a stale passphrase
                else -> null
            },
        )
        val errors = ServerValidation.validate(draft, current.canKeepSecret)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(validationErrors = errors, error = null) }
            return
        }

        _state.update { it.copy(saving = true, validationErrors = emptyList(), error = null) }
        viewModelScope.launch {
            val keySummary = if (current.authType == AuthType.PRIVATE_KEY) {
                val typedKey = current.privateKey.takeIf { it.isNotBlank() }
                if (typedKey == null && current.canKeepSecret) {
                    current.storedKeySummary
                } else {
                    val inspectResult = withContext(Dispatchers.IO) {
                        runCatching {
                            PrivateKeyInspector.inspect(
                                privateKeyPem = typedKey ?: "",
                                passphrase = current.passphrase.takeIf { it.isNotEmpty() },
                            )
                        }
                    }
                    inspectResult.fold(
                        onSuccess = { it.display },
                        onFailure = {
                            _state.update {
                                it.copy(
                                    saving = false,
                                    error = "Could not read the private key. Check the format and passphrase.",
                                )
                            }
                            return@launch
                        },
                    )
                }
            } else {
                null
            }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    repository.save(draft.copy(keySummary = keySummary))
                }
            }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, saved = true) }
                },
                onFailure = {
                    _state.update {
                        it.copy(saving = false, error = "Could not save the server. Please try again.")
                    }
                },
            )
        }
    }

    fun delete() {
        val id = _state.value.serverId
        if (id == 0L) return
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(deleted = true) }
        }
    }

    private fun ServerEditUiState.fromServer(server: Server): ServerEditUiState = copy(
        loading = false,
        name = server.name,
        host = server.host,
        port = server.port.toString(),
        username = server.username,
        authType = server.authType,
        originalAuthType = server.authType,
        groupName = server.groupName.orEmpty(),
        favorite = server.favorite,
        hasStoredSecret = server.hasSecret,
        storedKeySummary = server.keySummary,
    )

    companion object {
        private const val MAX_KEY_BYTES = 128 * 1024
    }
}

private fun android.content.ContentResolver.displayName(uri: Uri): String? {
    return runCatching {
        query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}

private fun readLimited(stream: java.io.InputStream, limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) {
            output.write(buffer, 0, limit - (total - read))
            break
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
