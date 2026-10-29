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

enum class ValidationError {
    NAME_REQUIRED,
    HOST_REQUIRED,
    HOST_SPACES,
    PORT_INVALID,
    USERNAME_REQUIRED,
    PASSWORD_REQUIRED,
    PRIVATE_KEY_REQUIRED,
}

object ServerValidation {
    fun validate(draft: ServerDraft, hasStoredSecret: Boolean): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        if (draft.name.isBlank()) errors += ValidationError.NAME_REQUIRED
        if (draft.host.isBlank()) errors += ValidationError.HOST_REQUIRED
        if (draft.host.any { it.isWhitespace() }) errors += ValidationError.HOST_SPACES
        if (draft.port !in 1..65535) errors += ValidationError.PORT_INVALID
        if (draft.username.isBlank()) errors += ValidationError.USERNAME_REQUIRED
        if (draft.newSecret.isNullOrBlank() && !hasStoredSecret) {
            errors += when (draft.authType) {
                AuthType.PASSWORD -> ValidationError.PASSWORD_REQUIRED
                AuthType.PRIVATE_KEY -> ValidationError.PRIVATE_KEY_REQUIRED
            }
        }
        return errors
    }
}
