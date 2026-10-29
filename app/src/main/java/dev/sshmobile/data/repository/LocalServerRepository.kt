package dev.sshmobile.data.repository

import dev.sshmobile.core.database.dao.ServerDao
import dev.sshmobile.core.database.entity.ServerEntity
import dev.sshmobile.core.security.SecretCipher
import dev.sshmobile.domain.model.AuthType
import dev.sshmobile.domain.model.Server
import dev.sshmobile.domain.model.ServerCredentials
import dev.sshmobile.domain.model.ServerDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stores server metadata in Room and encrypts every secret with the Android Keystore before it
 * reaches the database. Decryption happens only when a connection is about to be opened.
 */
class LocalServerRepository(
    private val dao: ServerDao,
    private val cipher: SecretCipher,
    private val clock: () -> Long = System::currentTimeMillis,
) : ServerRepository {

    override fun observeServers(): Flow<List<Server>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getServer(id: Long): Server? = dao.findById(id)?.toDomain()

    override suspend fun save(draft: ServerDraft): Long {
        val now = clock()
        val existing = if (draft.id != 0L) dao.findById(draft.id) else null
        val secret = if (draft.newSecret != null) {
            cipher.encrypt(draft.newSecret)
        } else {
            existing?.secret
        }
        val passphrase = if (draft.newPassphrase != null) {
            cipher.encrypt(draft.newPassphrase)
        } else {
            existing?.passphrase
        }
        if (secret == null) {
            throw IllegalArgumentException("A secret is required for the selected authentication type")
        }

        val entity = ServerEntity(
            id = draft.id,
            name = draft.name.trim(),
            host = draft.host.trim(),
            port = draft.port,
            username = draft.username.trim(),
            authType = draft.authType.name,
            groupName = draft.groupName?.trim()?.takeIf { it.isNotEmpty() },
            favorite = draft.favorite,
            secret = secret,
            passphrase = passphrase,
            keySummary = when (draft.authType) {
                AuthType.PASSWORD -> null
                AuthType.PRIVATE_KEY -> draft.keySummary ?: existing?.keySummary ?: "SSH private key"
            },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return if (existing == null) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            entity.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun setFavorite(id: Long, favorite: Boolean) =
        dao.setFavorite(id, favorite, clock())

    override suspend fun credentials(id: Long): ServerCredentials? {
        val entity = dao.findById(id) ?: return null
        val encryptedSecret = entity.secret ?: return null
        val secret = try {
            cipher.decrypt(encryptedSecret)
        } catch (e: Exception) {
            throw CredentialsUnavailableException()
        }
        return when (AuthType.valueOf(entity.authType)) {
            AuthType.PASSWORD -> ServerCredentials.Password(secret)
            AuthType.PRIVATE_KEY -> {
                val passphrase = entity.passphrase?.let {
                    try {
                        cipher.decrypt(it)
                    } catch (e: Exception) {
                        throw CredentialsUnavailableException("Stored key passphrase could not be decrypted")
                    }
                }?.takeIf { it.isNotEmpty() }
                ServerCredentials.PrivateKey(secret, passphrase)
            }
        }
    }

    override suspend fun clearAll(): Int {
        val count = dao.getAll().size
        dao.deleteAll()
        return count
    }

    private fun ServerEntity.toDomain(): Server = Server(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authType = AuthType.valueOf(authType),
        groupName = groupName,
        favorite = favorite,
        hasSecret = secret != null,
        keySummary = keySummary,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
