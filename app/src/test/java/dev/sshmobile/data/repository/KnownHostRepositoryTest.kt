package dev.sshmobile.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sshmobile.core.database.AppDatabase
import dev.sshmobile.domain.model.KnownHostKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KnownHostRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RoomKnownHostRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomKnownHostRepository(database.knownHostDao(), clock = { 5_000L })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `stores keys per host, port and algorithm`() = runTest {
        repository.save(
            KnownHostKey("example.com", 22, "ssh-ed25519", "blob1", "SHA256:one"),
        )
        repository.save(
            KnownHostKey("example.com", 22, "ssh-rsa", "blob2", "SHA256:two"),
        )
        repository.save(
            KnownHostKey("example.com", 2222, "ssh-ed25519", "blob3", "SHA256:three"),
        )

        val keys = repository.keysFor("example.com", 22)
        assertEquals(2, keys.size)
        assertEquals(setOf("ssh-ed25519", "ssh-rsa"), keys.map { it.keyType }.toSet())
        assertEquals(1, repository.keysFor("example.com", 2222).size)
        assertEquals(3, repository.observeCount().first())
    }

    @Test
    fun `updating a key preserves the first seen timestamp`() = runTest {
        repository.save(KnownHostKey("host", 22, "ssh-rsa", "old", "SHA256:old"))
        repository.save(KnownHostKey("host", 22, "ssh-rsa", "new", "SHA256:new"))

        val key = repository.keysFor("host", 22).single()
        assertEquals("new", key.keyBlob)
        assertEquals("SHA256:new", key.fingerprint)
        assertEquals(5_000L, key.addedAt)
        assertEquals(5_000L, key.lastSeenAt)
    }

    @Test
    fun `remove and clear`() = runTest {
        repository.save(KnownHostKey("a", 22, "ssh-rsa", "a", "SHA256:a"))
        repository.save(KnownHostKey("b", 22, "ssh-ed25519", "b", "SHA256:b"))

        repository.remove("a", 22, "ssh-rsa")
        assertTrue(repository.keysFor("a", 22).isEmpty())
        assertEquals(1, repository.observeAll().first().size)

        repository.clearAll()
        assertTrue(repository.observeAll().first().isEmpty())
    }
}
