package dev.sshmobile.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sshmobile.core.backup.ConfigBackup
import dev.sshmobile.core.backup.ConfigBackupCodec
import dev.sshmobile.core.backup.ServerConfigEntry
import dev.sshmobile.core.database.AppDatabase
import dev.sshmobile.domain.model.AuthType
import dev.sshmobile.domain.model.ServerDraft
import dev.sshmobile.testing.FakeSecretCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigBackupRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var serverRepository: LocalServerRepository
    private lateinit var backupRepository: ConfigBackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        serverRepository = LocalServerRepository(database.serverDao(), FakeSecretCipher(), clock = { 1_000L })
        backupRepository = ConfigBackupRepository(serverRepository, clock = { 2_000L })
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun draft(name: String, host: String = "example.com") = ServerDraft(
        name = name,
        host = host,
        port = 22,
        username = "tester",
        authType = AuthType.PASSWORD,
        groupName = null,
        favorite = false,
        newSecret = "hunter2",
    )

    @Test
    fun `export omits credentials and keeps jump host names`() = runTest {
        val gateway = serverRepository.save(draft("Gateway", "gw.example.com"))
        serverRepository.save(draft("Target", "10.0.0.5").copy(jumpServerId = gateway))

        val text = backupRepository.export()
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("\"password\""))
        assertFalse(text.contains("\"secret\""))

        val decoded = ConfigBackupCodec.decode(text)
        assertEquals(listOf("Gateway", "Target"), decoded.servers.map { it.name })
        assertEquals("Gateway", decoded.servers.first { it.name == "Target" }.jumpHostName)
        assertEquals(2_000L, decoded.exportedAt)
    }

    @Test
    fun `import creates servers without secrets and links jump hosts`() = runTest {
        val text = ConfigBackupCodec.encode(
            ConfigBackup(
                servers = listOf(
                    ServerConfigEntry("Gateway", "gw.example.com", 22, "ops", "PASSWORD"),
                    ServerConfigEntry(
                        "Target",
                        "10.0.0.5",
                        22,
                        "root",
                        "PRIVATE_KEY",
                        jumpHostName = "Gateway",
                    ),
                ),
            ),
        )

        val result = backupRepository.import(text)
        assertEquals(2, result.imported)
        assertEquals(0, result.skipped)
        assertTrue(result.errors.isEmpty())

        val servers = serverRepository.observeServers().first()
        assertEquals(setOf("Gateway", "Target"), servers.map { it.name }.toSet())
        assertTrue(servers.none { it.hasSecret })
        val gatewayId = servers.first { it.name == "Gateway" }.id
        assertEquals(gatewayId, servers.first { it.name == "Target" }.jumpServerId)
    }

    @Test
    fun `import reports unsupported auth types`() = runTest {
        val text = ConfigBackupCodec.encode(
            ConfigBackup(servers = listOf(ServerConfigEntry("Bad", "h", 22, "u", "KERBEROS"))),
        )
        val result = backupRepository.import(text)
        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertTrue(result.errors.single().contains("unsupported auth type"))
    }

    @Test
    fun `import reports a missing jump host`() = runTest {
        val text = ConfigBackupCodec.encode(
            ConfigBackup(
                servers = listOf(
                    ServerConfigEntry(
                        "Target",
                        "10.0.0.5",
                        22,
                        "root",
                        "PASSWORD",
                        jumpHostName = "Ghost",
                    ),
                ),
            ),
        )
        val result = backupRepository.import(text)
        assertEquals(1, result.imported)
        assertTrue(result.errors.single().contains("jump host 'Ghost' was not found"))
    }

    @Test
    fun `exported backup can be imported into an empty installation`() = runTest {
        serverRepository.save(draft("Alpha"))
        val text = backupRepository.export()

        serverRepository.clearAll()
        assertTrue(serverRepository.observeServers().first().isEmpty())

        val result = backupRepository.import(text)
        assertEquals(1, result.imported)
        assertEquals(listOf("Alpha"), serverRepository.observeServers().first().map { it.name })
    }
}
