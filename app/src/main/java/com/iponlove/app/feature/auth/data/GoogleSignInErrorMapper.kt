package com.iponlove.app.feature.auth.data

import com.iponlove.app.feature.auth.domain.model.AuthError

/**
 * Maps a Credential Manager failure to a user-facing outcome (ADR-0050 decision 6). Matched on
 * the exception's simple class name (and message) rather than the concrete `androidx.credentials`
 * types, so it stays a pure, JVM-unit-testable function with no Android classes on the classpath.
 *
 * A user dismissing the account picker is **not** an error — it returns `null` so the caller can
 * stay silent. Everything else surfaces a message.
 */
internal object GoogleSignInErrorMapper {

    /** `null` = the user cancelled (dismissed the sheet) → stay silent; otherwise the error to show. */
    fun classify(exceptionClassName: String?, message: String?): AuthError? {
        val name = exceptionClassName.orEmpty()
        val msg = message?.lowercase().orEmpty()
        return when {
            // GetCredentialCancellationException — the user tapped away from the picker.
            "Cancellation" in name -> null
            // NoCredentialException — no Google account is present on the device at all.
            "NoCredential" in name -> AuthError.GOOGLE_NO_ACCOUNT
            "host" in msg || "connect" in msg || "timeout" in msg ||
                "network" in msg || "unreachable" in msg -> AuthError.NETWORK
            else -> AuthError.GOOGLE_SIGN_IN_FAILED
        }
    }
}
