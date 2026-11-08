package dev.sshmobile.data.repository

import dev.sshmobile.core.backup.ConfigBackup
import dev.sshmobile.core.backup.ConfigBackupCodec
import dev.sshmobile.core.backup.ServerConfigEntry
import dev.sshmobile.domain.model.AuthType
import dev.sshmobile.domain.model.ServerDraft
import kotlinx.coroutines.flow.first

data class ConfigImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>,
)

/**
 * Exports server metadata as portable JSON and imports it back. Credentials never leave the
 * device: the exported file contains no passwords, private keys or passphrases, and imported
 * servers are saved without a secret so the user must complete them before connecting.
 */
class ConfigBackupRepository(
    private val serverRepository: ServerRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun export(): String {
        val servers = serverRepository.observeServers().first()
        val namesById = servers.associate { it.id to it.name }
        val entries = servers.map { server ->
            ServerConfigEntry(
                name = server.name,
                host = server.host,
                port = server.port,
                username = server.username,
                authType = server.authType.name,
                groupName = server.groupName,
                favorite = server.favorite,
                keySummary = server.keySummary,
                jumpHostName = server.jumpServerId?.let { namesById[it] },
            )
        }
        return ConfigBackupCodec.encode(ConfigBackup(exportedAt = clock(), servers = entries))
    }

    suspend fun import(text: String): ConfigImportResult {
        val backup = ConfigBackupCodec.decode(text)
        val idsByName = serverRepository.observeServers()
            .first()
            .associate { it.name to it.id }
            .toMutableMap()

        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        val pendingJump = mutableListOf<Pair<ServerConfigEntry, Long>>()

        // First pass: create every server without a jump host so that gateways referenced by
        // name later in the file already exist and can be resolved in the second pass.
        backup.servers.forEachIndexed { index, entry ->
            val authType = runCatching { AuthType.valueOf(entry.authType) }.getOrNull()
            if (authType == null) {
                skipped++
                errors += "Server #${index + 1} (${entry.name}): unsupported auth type '${entry.authType}'"
                return@forEachIndexed
            }
            try {
                val id = serverRepository.save(
                    entry.toDraft(authType = authType, jumpServerId = null),
                    allowMissingSecret = true,
                )
                idsByName[entry.name] = id
                pendingJump += entry to id
                imported++
            } catch (e: Exception) {
                skipped++
                errors += "Server #${index + 1} (${entry.name}): ${e.message ?: "import failed"}"
            }
        }

        // Second pass: resolve jump hosts by name now that all imported servers have ids.
        pendingJump.forEach { (entry, id) ->
            val jumpName = entry.jumpHostName ?: return@forEach
            val jumpId = idsByName[jumpName]
            if (jumpId == null) {
                errors += "Server (${entry.name}): jump host '$jumpName' was not found"
                return@forEach
            }
            if (jumpId == id) {
                errors += "Server (${entry.name}): cannot jump through itself"
                return@forEach
            }
            try {
                val authType = AuthType.valueOf(entry.authType)
                serverRepository.save(
                    entry.toDraft(authType = authType, jumpServerId = jumpId, id = id),
                    allowMissingSecret = true,
                )
            } catch (e: Exception) {
                errors += "Server (${entry.name}): ${e.message ?: "jump host could not be linked"}"
            }
        }

        return ConfigImportResult(imported = imported, skipped = skipped, errors = errors)
    }

    private fun ServerConfigEntry.toDraft(
        authType: AuthType,
        jumpServerId: Long?,
        id: Long = 0,
    ): ServerDraft = ServerDraft(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authType = authType,
        groupName = groupName,
        favorite = favorite,
        newSecret = null,
        newPassphrase = null,
        keySummary = keySummary,
        jumpServerId = jumpServerId,
    )
}
