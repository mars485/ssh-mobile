package dev.sshmobile.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sshmobile.core.security.SecretCipher
import dev.sshmobile.testing.FakeSecretCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalCommandRepositoryTest {

    @Test
    fun `history is opt in and snippets persist through the encrypted repository`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cipher = RecordingSecretCipher()
        val repository = TerminalCommandRepository(context, cipher)
        repository.clearAll()

        try {
            repository.recordCommand("echo should-not-be-saved")
            assertFalse(repository.library.first().historyEnabled)
            assertTrue(repository.library.first().history.isEmpty())

            repository.setHistoryEnabled(true)
            repository.recordCommand("echo hello")
            repository.recordCommand("echo hello")
            val snippetId = repository.saveSnippet("Service logs", "journalctl -u nginx\n tail -n 100")

            val library = repository.library.first()
            assertTrue(library.historyEnabled)
            assertEquals(listOf("echo hello"), library.history)
            assertEquals("Service logs", library.snippets.single().name)
            assertEquals("journalctl -u nginx\n tail -n 100", library.snippets.single().command)

            assertTrue(cipher.encryptedPayloads.size >= 2)
            assertTrue(cipher.encryptedPayloads.none { "echo hello" in it })
            assertTrue(cipher.encryptedPayloads.none { "journalctl -u nginx" in it })

            repository.saveSnippet("Service logs v2", "journalctl -u nginx", snippetId)
            assertEquals("Service logs v2", repository.library.first().snippets.single().name)
            repository.deleteSnippet(snippetId)
            assertTrue(repository.library.first().snippets.isEmpty())

            repository.clearHistory()
            assertTrue(repository.library.first().history.isEmpty())
        } finally {
            repository.clearAll()
        }
    }

    private class RecordingSecretCipher : SecretCipher {
        private val delegate = FakeSecretCipher()
        val encryptedPayloads = mutableListOf<String>()

        override fun encrypt(plaintext: String): String {
            encryptedPayloads += plaintext
            return delegate.encrypt(plaintext)
        }

        override fun decrypt(payload: String): String = delegate.decrypt(payload)
    }
}
