package dev.sshmobile.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.sshmobile.core.database.entity.KnownHostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun forHost(host: String, port: Int): List<KnownHostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KnownHostEntity)

    @Query("SELECT * FROM known_hosts ORDER BY host COLLATE NOCASE ASC, port ASC")
    fun observeAll(): Flow<List<KnownHostEntity>>

    @Query("SELECT COUNT(*) FROM known_hosts")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM known_hosts WHERE host = :host AND port = :port AND keyType = :keyType")
    suspend fun delete(host: String, port: Int, keyType: String)

    @Query("DELETE FROM known_hosts")
    suspend fun deleteAll()
}
