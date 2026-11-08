package dev.sshmobile.core.ssh

import dev.sshmobile.domain.model.ServerCredentials
import dev.sshmobile.testing.SshTestServer
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.util.concurrent.CopyOnWriteArrayList

class SftpEngineIntegrationTest {

    private val servers = mutableListOf<SshTestServer>()
    private val roots = mutableListOf<Path>()

    @After
    fun tearDown() {
        servers.forEach { it.close() }
        servers.clear()
        roots.forEach { root -> root.toFile().deleteRecursively() }
        roots.clear()
    }

    private fun startServer(): Pair<SshTestServer, Path> {
        val root = Files.createTempDirectory("sftp-test-root")
        roots.add(root)
        val server = SshTestServer(
            hostKeyPair = rsaKeyPair(),
            password = SshTestServer.PASSWORD,
            authorizedKey = null,
            sftpRoot = root,
        ).start()
        servers += server
        return server to root
    }

    private fun connect(server: SshTestServer): SshConnection {
        val verifier = KnownHostsHostKeyVerifier(
            host = "127.0.0.1",
            port = server.port,
            knownKeys = emptyList(),
            prompt = { true },
        )
        return runBlocking {
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
    }

    @Test
    fun `lists, creates, renames and removes entries`() {
        val (server, root) = startServer()
        Files.write(root.resolve("readme.txt"), "hello".toByteArray())
        Files.createDirectory(root.resolve("docs"))
        Files.write(root.resolve("docs").resolve("guide.md"), "guide".toByteArray())

        val connection = connect(server)
        try {
            val sftp = runBlocking { connection.openSftp() }
            try {
                assertEquals("/", runBlocking { sftp.canonicalize(".") })

                val rootEntries = runBlocking { sftp.list("/") }
                assertEquals(listOf("docs", "readme.txt"), rootEntries.map { it.name })
                assertTrue(rootEntries.first().isDirectory)
                assertFalse(rootEntries.last().isDirectory)
                assertEquals(5L, rootEntries.last().size)

                val created = runBlocking {
                    sftp.mkdir("/created/nested")
                    sftp.list("/created")
                }
                assertEquals(listOf("nested"), created.map { it.name })
                assertTrue(created.single().isDirectory)

                runBlocking { sftp.rename("/readme.txt", "/renamed.txt") }
                val afterRename = runBlocking { sftp.list("/") }.map { it.name }
                assertTrue(afterRename.contains("renamed.txt"))
                assertFalse(afterRename.contains("readme.txt"))

                runBlocking { sftp.remove("/created", recursive = true) }
                assertFalse(runBlocking { sftp.list("/") }.any { it.name == "created" })

                runBlocking { sftp.remove("/renamed.txt", recursive = false) }
                assertFalse(runBlocking { sftp.list("/") }.any { it.name == "renamed.txt" })
            } finally {
                sftp.close()
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun `changes permissions with an octal mode`() {
        val (server, root) = startServer()
        val script = root.resolve("run.sh")
        Files.write(script, "#!/bin/sh\n".toByteArray())

        val connection = connect(server)
        try {
            val sftp = runBlocking { connection.openSftp() }
            try {
                runBlocking { sftp.chmod("/run.sh", 0b111101101) }
                val entry = runBlocking { sftp.stat("/run.sh") }
                assertFalse(entry.isDirectory)
                // Windows file systems do not store POSIX mode bits; the server then reports 0666.
                // Android is POSIX, so the exact round trip is asserted there.
                if (!isWindows()) {
                    assertEquals(0b111101101, entry.permissions)
                } else {
                    assertTrue(entry.permissions in 0..0x1FF)
                }
            } finally {
                sftp.close()
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun `uploads and downloads files with progress`() {
        val (server, root) = startServer()
        val payload = ByteArray(200_000) { index -> (index % 251).toByte() }

        val connection = connect(server)
        try {
            val sftp = runBlocking { connection.openSftp() }
            try {
                val uploadProgress = CopyOnWriteArrayList<Long>()
                runBlocking {
                    sftp.upload(
                        "/payload.bin",
                        ByteArrayInputStream(payload),
                        onProgress = { uploadProgress += it },
                    )
                }
                assertTrue(uploadProgress.isNotEmpty())
                assertEquals(payload.size.toLong(), uploadProgress.last())
                assertEquals(payload.size.toLong(), runBlocking { sftp.stat("/payload.bin") }.size)

                val sink = ByteArrayOutputStream()
                val downloadProgress = CopyOnWriteArrayList<Long>()
                runBlocking {
                    sftp.download(
                        "/payload.bin",
                        sink,
                        onProgress = { downloadProgress += it },
                    )
                }
                assertArrayEquals(payload, sink.toByteArray())
                assertEquals(payload.size.toLong(), downloadProgress.last())
            } finally {
                sftp.close()
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun `missing paths are reported as errors`() {
        val (server, _) = startServer()
        val connection = connect(server)
        try {
            val sftp = runBlocking { connection.openSftp() }
            try {
                val error = runCatching { runBlocking { sftp.list("/does-not-exist") } }.exceptionOrNull()
                assertTrue("expected an IOException, got $error", error is java.io.IOException)
            } finally {
                sftp.close()
            }
        } finally {
            connection.close()
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

        private fun isWindows(): Boolean =
            System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

        private fun rsaKeyPair(): KeyPair =
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
}
