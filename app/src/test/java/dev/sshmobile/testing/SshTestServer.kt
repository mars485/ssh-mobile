package dev.sshmobile.testing

import org.apache.sshd.common.channel.PtyMode
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.shell.ShellFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.PublicKey
import java.util.concurrent.CopyOnWriteArrayList

data class ShellRecord(
    val columns: Int,
    val rows: Int,
    val terminal: String,
    val ptyModes: Map<PtyMode, Int>,
)

/**
 * Small in-process SSH server used by the JVM integration tests. It performs a real key exchange,
 * password/public-key authentication and PTY shell allocation, so the SSHJ client configuration is
 * exercised end-to-end without touching any external infrastructure.
 */
class SshTestServer(
    private val hostKeyPair: KeyPair,
    private val password: String?,
    private val authorizedKey: PublicKey?,
) : AutoCloseable {

    val shells = CopyOnWriteArrayList<ShellRecord>()
    private lateinit var server: SshServer

    fun start(port: Int = 0): SshTestServer {
        server = SshServer.setUpDefaultServer()
        server.host = "127.0.0.1"
        server.port = port
        server.keyPairProvider = KeyPairProvider.wrap(hostKeyPair)
        if (password != null) {
            server.passwordAuthenticator = PasswordAuthenticator { username, candidate, _ ->
                username == USERNAME && candidate == password
            }
        }
        if (authorizedKey != null) {
            server.publickeyAuthenticator = PublickeyAuthenticator { username, key, _ ->
                username == USERNAME && key == authorizedKey
            }
        }
        server.shellFactory = ShellFactory { _ -> EchoShell(shells) }
        server.start()
        return this
    }

    val port: Int
        get() = (server.boundAddresses.first() as InetSocketAddress).port

    override fun close() {
        runCatching { server.stop(true) }
    }

    companion object {
        const val USERNAME = "tester"
        const val PASSWORD = "correct-horse-battery"
    }

    private class EchoShell(
        private val records: MutableList<ShellRecord>,
    ) : Command {
        private var input: InputStream? = null
        private var output: OutputStream? = null
        private var error: OutputStream? = null
        private var callback: ExitCallback? = null
        private var worker: Thread? = null

        override fun setInputStream(input: InputStream) {
            this.input = input
        }

        override fun setOutputStream(output: OutputStream) {
            this.output = output
        }

        override fun setErrorStream(error: OutputStream) {
            this.error = error
        }

        override fun setExitCallback(callback: ExitCallback) {
            this.callback = callback
        }

        override fun start(channel: ChannelSession, env: Environment) {
            val columns = env.env[Environment.ENV_COLUMNS]?.toIntOrNull() ?: 0
            val rows = env.env[Environment.ENV_LINES]?.toIntOrNull() ?: 0
            val terminal = env.env[Environment.ENV_TERM].orEmpty()
            records += ShellRecord(columns, rows, terminal, env.ptyModes)
            val out = output ?: return
            out.write("SSH READY ${columns}x${rows} term=$terminal\r\n".toByteArray())
            out.flush()
            worker = Thread {
                val buffer = ByteArray(4096)
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        val read = input?.read(buffer) ?: -1
                        if (read < 0) break
                        if (read > 0) {
                            out.write(buffer, 0, read)
                            out.flush()
                        }
                    }
                    callback?.onExit(0)
                } catch (_: IOException) {
                    callback?.onExit(0)
                }
            }.apply {
                name = "ssh-test-shell"
                isDaemon = true
                start()
            }
        }

        override fun destroy(channel: ChannelSession) {
            worker?.interrupt()
            runCatching { input?.close() }
            runCatching { output?.close() }
        }
    }
}
