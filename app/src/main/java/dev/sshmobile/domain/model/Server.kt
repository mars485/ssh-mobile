package dev.sshmobile.domain.model

enum class AuthType {
    PASSWORD,
    PRIVATE_KEY,
}

/**
 * Server metadata exposed to the UI. Credentials are deliberately not part of this model.
 */
data class Server(
    val id: Long,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: AuthType,
    val groupName: String?,
    val favorite: Boolean,
    val hasSecret: Boolean,
    val keySummary: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Credentials are decrypted on demand, immediately before a connection is opened. */
sealed interface ServerCredentials {
    data class Password(val password: String) : ServerCredentials

    data class PrivateKey(
        val privateKeyPem: String,
        val passphrase: String?,
    ) : ServerCredentials
}

data class ServerDraft(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: AuthType,
    val groupName: String?,
    val favorite: Boolean,
    /**
     * Plaintext password or private key entered by the user. Null while editing means
     * "keep the previously stored secret".
     */
    val newSecret: String? = null,
    /** Plaintext passphrase; null means "keep the stored passphrase". */
    val newPassphrase: String? = null,
    val keySummary: String? = null,
)

object ServerValidation {
    fun validate(draft: ServerDraft, hasStoredSecret: Boolean): List<String> {
        val errors = mutableListOf<String>()
        if (draft.name.isBlank()) errors += "Name is required"
        if (draft.host.isBlank()) errors += "Host is required"
        if (draft.host.any { it.isWhitespace() }) errors += "Host must not contain spaces"
        if (draft.port !in 1..65535) errors += "Port must be between 1 and 65535"
        if (draft.username.isBlank()) errors += "Username is required"
        if (draft.newSecret.isNullOrBlank() && !hasStoredSecret) {
            errors += when (draft.authType) {
                AuthType.PASSWORD -> "Password is required"
                AuthType.PRIVATE_KEY -> "Private key is required"
            }
        }
        return errors
    }
}
