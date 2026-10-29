package dev.sshmobile.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.sshmobile.core.database.dao.KnownHostDao
import dev.sshmobile.core.database.dao.ServerDao
import dev.sshmobile.core.database.entity.KnownHostEntity
import dev.sshmobile.core.database.entity.ServerEntity

@Database(
    entities = [ServerEntity::class, KnownHostEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun knownHostDao(): KnownHostDao

    companion object {
        const val NAME = "sshmobile.db"
    }
}
