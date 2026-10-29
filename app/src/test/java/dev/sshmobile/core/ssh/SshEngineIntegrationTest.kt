package dev.sshmobile.core.ssh

import dev.sshmobile.core.terminal.TerminalEmulator
import dev.sshmobile.domain.model.HostKeyInfo
import dev.sshmobile.domain.model.KnownHostKey
import dev.sshmobile.domain.model.ServerCredentials
import dev.sshmobile.testing.SshTestServer
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringWriter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import kotlin.concurrent.thread

class SshEngineIntegrationTest {

    private val servers = mutableListOf<SshTestServer>()

    @After
    fun tearDown() {
        servers.forEach { it.close() }
        servers.clear()
    }

    private fun startServer(
        hostKey: KeyPair = rsaKeyPair(),
        password: String? = SshTestServer.PASSWORD,
        authorizedKey: java.security.PublicKey? = null,
        port: Int = 0,
    ): SshTestServer {
        val server = SshTestServer(hostKey, password, authorizedKey).start(port)
        servers += server
        return server
    }

    @Test
    fun `password authentication, host key prompt and interactive PTY`() {
        val server = startServer()
        val challenges = ArrayList<dev.sshmobile.domain.model.HostKeyChallenge>()
        val verifier = KnownHostsHostKeyVerifier(
            host = "127.0.0.1",
            port = server.port,
            knownKeys = emptyList(),
            prompt = { challenge ->
                challenges += challenge
                challenge.fingerprint.startsWith("SHA256:")
            },
        )

        val connection = runBlocking {
            SshjEngine().connect(
                SshConnectionConfig(
                    host = "127.0.0.1",
                    port = server.port,
                    username = SshTestServer.USERNAME,
                    credentials = ServerCredentials.Password(SshTestServer.PASSWORD),
                ),
                verifier,
            )
        }
        try {
            assertEquals(1, challenges.size)
            assertEquals(connection.hostKey.fingerprint, challenges[0].fingerprint)
            assertFalse(challenges[0].changed)
            assertTrue(challenges[0].keyBlob.isNotBlank())

            val shell = runBlocking { connection.openShell(80, 24) }
            val collector = OutputCollector(shell.input)
            try {
                assertTrue("server banner was not received", collector.awaitContains("SSH READY", 10_000))
                assertTrue(collector.snapshot().contains("80x24"))

                val record = server.shells.first()
                assertEquals(80, record.columns)
                assertEquals(24, record.rows)
                assertEquals("xterm-256color", record.terminal)

                shell.output.write("echo hello\r\n".toByteArray())
                shell.output.flush()
                assertTrue("shell did not echo input", collector.awaitContains("echo hello", 10_000))

                val terminal = TerminalEmulator(cols = 80, rows = 24)
                terminal.feed(collector.snapshot().toByteArray())
                assertTrue(terminal.dumpText().contains("echo hello"))

                runBlocking { shell.resize(100, 40) }
            } finally {
                runCatching { shell.close() }
                collector.stop()
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun `public key authentication`() {
        val clientKey = rsaKeyPair()
        val server = startServer(password = null, authorizedKey = clientKey.public)
        val pem = privateKeyPem(clientKey)

        val summary = PrivateKeyInspector.inspect(pem, null)
        assertTrue(summary.keyType.contains("rsa", ignoreCase = true))
        assertTrue(summary.fingerprint.startsWith("SHA256:"))

        val connection = runBlocking {
            SshjEngine().connect(
                SshConnectionConfig(
                    host = "127.0.0.1",
                    port = server.port,
                    username = SshTestServer.USERNAME,
                    credentials = ServerCredentials.PrivateKey(pem, null),
                ),
                KnownHostsHostKeyVerifier(
                    host = "127.0.0.1",
                    port = server.port,
                    knownKeys = emptyList(),
                    prompt = { true },
                ),
            )
        }
        try {
            val shell = runBlocking { connection.openShell(120, 40) }
            val collector = OutputCollector(shell.input)
            try {
                assertTrue(collector.awaitContains("SSH READY 120x40", 10_000))
            } finally {
                runCatching { shell.close() }
                collector.stop()
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun `wrong password is reported as an auth error`() {
        val server = startServer()
        val error = runCatching {
            runBlocking {
                SshjEngine().connect(
                    SshConnectionConfig(
                        host = "127.0.0.1",
                        port = server.port,
                        username = SshTestServer.USERNAME,
                        credentials = ServerCredentials.Password("wrong-password"),
                    ),
                    KnownHostsHostKeyVerifier(
                        host = "127.0.0.1",
                        port = server.port,
                        knownKeys = emptyList(),
                        prompt = { true },
                    ),
                )
            }
        }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is SshException)
        assertEquals(SshErrorKind.AUTH, (error as SshException).kind)
    }

    @Test
    fun `rejecting the host key aborts the connection`() {
        val server = startServer()
        var prompted = false
        val error = runCatching {
            runBlocking {
                SshjEngine().connect(
                    SshConnectionConfig(
                        host = "127.0.0.1",
                        port = server.port,
                        username = SshTestServer.USERNAME,
                        credentials = ServerCredentials.Password(SshTestServer.PASSWORD),
                    ),
                    KnownHostsHostKeyVerifier(
                        host = "127.0.0.1",
                        port = server.port,
                        knownKeys = emptyList(),
                        prompt = {
                            prompted = true
                            false
                        },
                    ),
                )
            }
        }.exceptionOrNull()
        assertTrue(prompted)
        assertNotNull(error)
        assertTrue(
            "expected a host-key or transport error, got $error",
            error is SshException &&
                (error.kind == SshErrorKind.HOST_KEY || error.kind == SshErrorKind.NETWORK),
        )
    }

    @Test
    fun `matching known key does not prompt`() {
        val blob = "AAAAtestblob"
        val info = HostKeyInfo("ssh-ed25519", blob, HostKeys.fingerprintOfBase64(blob))
        var prompted = false
        val verifier = KnownHostsHostKeyVerifier(
            host = "example.com",
            port = 22,
            knownKeys = listOf(
                KnownHostKey("example.com", 22, "ssh-ed25519", blob, info.fingerprint),
            ),
            prompt = {
                prompted = true
                true
            },
        )
        assertTrue(verifier.verify(info))
        assertFalse(prompted)
        assertEquals(listOf("ssh-ed25519"), verifier.existingKeyTypes())
    }

    @Test
    fun `changed key is reported with the previous fingerprint`() {
        val oldBlob = "AAAABBBBCCCC"
        val newBlob = "DDDDEEEEFFFF"
        val oldFingerprint = HostKeys.fingerprintOfBase64(oldBlob)
        val newFingerprint = HostKeys.fingerprintOfBase64(newBlob)
        var challenge: dev.sshmobile.domain.model.HostKeyChallenge? = null
        val verifier = KnownHostsHostKeyVerifier(
            host = "example.com",
            port = 2222,
            knownKeys = listOf(
                KnownHostKey("example.com", 2222, "ssh-rsa", oldBlob, oldFingerprint),
            ),
            prompt = {
                challenge = it
                false
            },
        )
        val accepted = verifier.verify(HostKeyInfo("ssh-rsa", newBlob, newFingerprint))
        assertFalse(accepted)
        assertNotNull(challenge)
        assertTrue(challenge!!.changed)
        assertEquals(newFingerprint, challenge!!.fingerprint)
        assertEquals(oldFingerprint, challenge!!.previousFingerprint)
    }

    @Test
    fun `invalid private key is rejected with a clear error`() {
        val error = runCatching {
            PrivateKeyInspector.inspect("not a key", null)
        }.exceptionOrNull()
        assertNotNull(error)
    }

    private class OutputCollector(input: InputStream) {
        private val output = ByteArrayOutputStream()
        private val lock = Any()
        private val reader = thread(start = true, isDaemon = true, name = "ssh-test-reader") {
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) synchronized(lock) { output.write(buffer, 0, read) }
                }
            } catch (_: Exception) {
                // Stream closed at the end of the test.
            }
        }

        fun snapshot(): String = synchronized(lock) { output.toString(Charsets.UTF_8.name()) }

        fun awaitContains(text: String, timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (snapshot().contains(text)) return true
                Thread.sleep(20)
            }
            return snapshot().contains(text)
        }

        fun stop() {
            reader.interrupt()
        }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun installBouncyCastle() {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        private fun rsaKeyPair(): KeyPair =
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        private fun privateKeyPem(keyPair: KeyPair): String {
            val writer = StringWriter()
            JcaPEMWriter(writer).use { it.writeObject(keyPair.private) }
            return writer.toString()
        }
    }
}
