package dev.sshmobile.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sshmobile.core.database.AppDatabase
import dev.sshmobile.domain.model.AuthType
import dev.sshmobile.domain.model.ServerCredentials
import dev.sshmobile.domain.model.ServerDraft
import dev.sshmobile.domain.model.ServerValidation
import dev.sshmobile.testing.FakeSecretCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalServerRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var cipher: FakeSecretCipher
    private lateinit var repository: LocalServerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cipher = FakeSecretCipher()
        repository = LocalServerRepository(database.serverDao(), cipher, clock = { 1_000L })
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun passwordDraft(
        name: String = "Test server",
        favorite: Boolean = false,
        group: String? = null,
        password: String = "hunter2",
    ) = ServerDraft(
        name = name,
        host = "example.com",
        port = 22,
        username = "tester",
        authType = AuthType.PASSWORD,
        groupName = group,
        favorite = favorite,
        newSecret = password,
    )

    @Test
    fun `password is encrypted at rest and decrypted on demand`() = runTest {
        val id = repository.save(passwordDraft())
        val entity = database.serverDao().findById(id)!!
        assertNotNull(entity.secret)
        assertFalse(entity.secret!!.contains("hunter2"))
        assertTrue(entity.secret!!.startsWith(FakeSecretCipher.PREFIX))

        val server = repository.getServer(id)!!
        assertTrue(server.hasSecret)
        assertFalse(server.toString().contains("hunter2"))
        assertNull(server.keySummary)

        val credentials = repository.credentials(id)
        assertEquals(ServerCredentials.Password("hunter2"), credentials)
    }

    @Test
    fun `private key and passphrase are both encrypted`() = runTest {
        val pem = "-----BEGIN OPENSSH PRIVATE KEY-----\nsecret-key-material\n-----END OPENSSH PRIVATE KEY-----"
        val id = repository.save(
            ServerDraft(
                name = "Key server",
                host = "key.example.com",
                port = 2222,
                username = "git",
                authType = AuthType.PRIVATE_KEY,
                groupName = "Work",
                favorite = true,
                newSecret = pem,
                newPassphrase = "key-passphrase",
                keySummary = "ED25519 · SHA256:abc…",
            ),
        )
        val entity = database.serverDao().findById(id)!!
        assertFalse(entity.secret!!.contains("secret-key-material"))
        assertFalse(entity.passphrase!!.contains("key-passphrase"))

        val credentials = repository.credentials(id) as ServerCredentials.PrivateKey
        assertEquals(pem, credentials.privateKeyPem)
        assertEquals("key-passphrase", credentials.passphrase)

        val server = repository.getServer(id)!!
        assertEquals(AuthType.PRIVATE_KEY, server.authType)
        assertEquals("ED25519 · SHA256:abc…", server.keySummary)
    }

    @Test
    fun `editing without a new secret keeps the stored ciphertext`() = runTest {
        val id = repository.save(passwordDraft(password = "first-password"))
        val before = database.serverDao().findById(id)!!.secret

        repository.save(
            ServerDraft(
                id = id,
                name = "Renamed",
                host = "example.com",
                port = 22,
                username = "tester",
                authType = AuthType.PASSWORD,
                groupName = "Home",
                favorite = false,
                newSecret = null,
                newPassphrase = null,
            ),
        )

        val after = database.serverDao().findById(id)!!
        assertEquals(before, after.secret)
        assertEquals("Renamed", after.name)
        assertEquals("Home", after.groupName)
        assertEquals(ServerCredentials.Password("first-password"), repository.credentials(id))
    }

    @Test
    fun `favorites are listed first`() = runTest {
        repository.save(passwordDraft(name = "Beta"))
        repository.save(passwordDraft(name = "Alpha", favorite = true))
        repository.save(passwordDraft(name = "Gamma"))

        val servers = repository.observeServers().first()
        assertEquals(listOf("Alpha", "Beta", "Gamma"), servers.map { it.name })
        assertTrue(servers.first().favorite)
    }

    @Test
    fun `favorite toggling and deletion work`() = runTest {
        val id = repository.save(passwordDraft())
        repository.setFavorite(id, true)
        assertTrue(repository.getServer(id)!!.favorite)

        repository.delete(id)
        assertNull(repository.getServer(id))
        assertNull(repository.credentials(id))
    }

    @Test
    fun `corrupted secret raises credentials unavailable`() = runTest {
        val id = repository.save(passwordDraft())
        val dao = database.serverDao()
        val entity = dao.findById(id)!!
        dao.update(entity.copy(secret = "garbage"))

        val error = runCatching { repository.credentials(id) }.exceptionOrNull()
        assertTrue(error is CredentialsUnavailableException)
    }

    @Test
    fun `clearAll returns the number of removed servers`() = runTest {
        repository.save(passwordDraft(name = "One"))
        repository.save(passwordDraft(name = "Two"))
        assertEquals(2, repository.clearAll())
        assertTrue(repository.observeServers().first().isEmpty())
    }

    @Test
    fun `validation requires a secret for new and re-authenticated servers`() {
        val draft = passwordDraft(password = "")
        assertTrue(ServerValidation.validate(draft, hasStoredSecret = false).any { it.contains("Password") })
        assertTrue(ServerValidation.validate(draft, hasStoredSecret = true).isEmpty())

        val keyDraft = draft.copy(authType = AuthType.PRIVATE_KEY)
        assertTrue(ServerValidation.validate(keyDraft, hasStoredSecret = false).any { it.contains("key") })
    }
}
