package dev.sshmobile.data.repository

import dev.sshmobile.core.database.dao.KnownHostDao
import dev.sshmobile.core.database.entity.KnownHostEntity
import dev.sshmobile.domain.model.KnownHostKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface KnownHostRepository {
    suspend fun keysFor(host: String, port: Int): List<KnownHostKey>
    suspend fun save(key: KnownHostKey)
    suspend fun remove(host: String, port: Int, keyType: String)
    suspend fun clearAll()
    fun observeCount(): Flow<Int>
    fun observeAll(): Flow<List<KnownHostKey>>
}

class RoomKnownHostRepository(
    private val dao: KnownHostDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : KnownHostRepository {

    override suspend fun keysFor(host: String, port: Int): List<KnownHostKey> =
        dao.forHost(host, port).map { it.toDomain() }

    override suspend fun save(key: KnownHostKey) {
        val now = clock()
        val existing = dao.forHost(key.host, key.port).firstOrNull { it.keyType == key.keyType }
        dao.upsert(
            KnownHostEntity(
                host = key.host,
                port = key.port,
                keyType = key.keyType,
                keyBlob = key.keyBlob,
                fingerprint = key.fingerprint,
                addedAt = existing?.addedAt ?: now,
                lastSeenAt = now,
            ),
        )
    }

    override suspend fun remove(host: String, port: Int, keyType: String) =
        dao.delete(host, port, keyType)

    override suspend fun clearAll() = dao.deleteAll()

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override fun observeAll(): Flow<List<KnownHostKey>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    private fun KnownHostEntity.toDomain() = KnownHostKey(
        host = host,
        port = port,
        keyType = keyType,
        keyBlob = keyBlob,
        fingerprint = fingerprint,
        addedAt = addedAt,
        lastSeenAt = lastSeenAt,
    )
}
