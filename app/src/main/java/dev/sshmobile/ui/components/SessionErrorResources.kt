package dev.sshmobile.ui.components

import androidx.annotation.StringRes
import dev.sshmobile.R
import dev.sshmobile.core.session.SessionError

/** Localized message for a platform-neutral session error code. */
@StringRes
fun sessionErrorRes(error: SessionError): Int = when (error) {
    SessionError.SESSION_UNAVAILABLE -> R.string.error_session_unavailable
    SessionError.SERVER_DELETED -> R.string.error_server_missing
    SessionError.NO_CREDENTIALS -> R.string.error_no_credentials
    SessionError.CREDENTIALS_UNAVAILABLE -> R.string.error_credentials_unavailable
    SessionError.HOST_KEY_REJECTED -> R.string.error_host_key_rejected
    SessionError.HOST_KEY_TIMEOUT -> R.string.error_host_key_timeout
    SessionError.AUTH_FAILED -> R.string.error_auth_failed
    SessionError.HOST_NOT_FOUND -> R.string.error_host_not_found
    SessionError.CONNECT_TIMEOUT -> R.string.error_connect_timeout
    SessionError.CONNECTION_REFUSED -> R.string.error_connection_refused
    SessionError.NETWORK -> R.string.error_network
    SessionError.KEY_FORMAT -> R.string.error_key_format
    SessionError.PROTOCOL -> R.string.error_protocol
    SessionError.SESSION_ENDED -> R.string.error_session_ended
    SessionError.CONNECTION_LOST -> R.string.error_connection_lost
    SessionError.AUTO_RECONNECT_DISABLED -> R.string.error_connection_lost_no_reconnect
    SessionError.UNKNOWN -> R.string.error_unknown
}
