package dev.sshmobile

import android.content.Context
import androidx.room.Room
import dev.sshmobile.core.database.AppDatabase
import dev.sshmobile.core.security.AndroidKeystoreSecretCipher
import dev.sshmobile.core.security.SecretCipher
import dev.sshmobile.core.session.SshSessionManager
import dev.sshmobile.core.ssh.SshEngine
import dev.sshmobile.core.ssh.SshjEngine
import dev.sshmobile.data.repository.KnownHostRepository
import dev.sshmobile.data.repository.LocalServerRepository
import dev.sshmobile.data.repository.RoomKnownHostRepository
import dev.sshmobile.data.repository.ServerRepository
import dev.sshmobile.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Manual dependency container. Keeping it explicit (instead of a DI framework) makes the
 * dependency graph and the lifetime of the SSH session manager easy to audit.
 */
class AppContainer(
    context: Context,
    val appScope: CoroutineScope,
) {
    private val applicationContext = context.applicationContext

    private val database: AppDatabase = Room.databaseBuilder(
        applicationContext,
        AppDatabase::class.java,
        AppDatabase.NAME,
    ).build()

    val secretCipher: SecretCipher = AndroidKeystoreSecretCipher()

    val serverRepository: ServerRepository = LocalServerRepository(
        dao = database.serverDao(),
        cipher = secretCipher,
    )

    val knownHostRepository: KnownHostRepository = RoomKnownHostRepository(database.knownHostDao())

    val settingsRepository = SettingsRepository(applicationContext)

    val sshEngine: SshEngine = SshjEngine()

    val sessionManager = SshSessionManager(
        engine = sshEngine,
        serverRepository = serverRepository,
        knownHostRepository = knownHostRepository,
        settingsRepository = settingsRepository,
        scope = appScope,
    )

    fun wipeCredentials() {
        (secretCipher as? AndroidKeystoreSecretCipher)?.deleteKey()
    }
}
