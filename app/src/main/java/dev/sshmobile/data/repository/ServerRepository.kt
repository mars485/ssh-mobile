package dev.sshmobile.data.repository

import dev.sshmobile.domain.model.Server
import dev.sshmobile.domain.model.ServerCredentials
import dev.sshmobile.domain.model.ServerDraft
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    fun observeServers(): Flow<List<Server>>
    suspend fun getServer(id: Long): Server?
    /**
     * Creates or updates a server. [allowMissingSecret] is only used by configuration import:
     * imported servers carry no credentials and must be completed by the user before connecting.
     */
    suspend fun save(draft: ServerDraft, allowMissingSecret: Boolean = false): Long
    suspend fun delete(id: Long)
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun credentials(id: Long): ServerCredentials?
    suspend fun clearAll(): Int
}

class CredentialsUnavailableException(message: String = "Stored credentials could not be decrypted") :
    Exception(message)
