package com.iponlove.app.feature.auth.domain.model

/** A user-facing reason an auth call failed; the presentation layer maps it to a message. */
enum class AuthError {
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    EMAIL_ALREADY_REGISTERED,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    INVALID_NAME,
    PASSWORD_MISMATCH,
    SAME_AS_OLD_PASSWORD,
    RATE_LIMITED,
    // No Google account is present on the device (Credential Manager NoCredentialException).
    GOOGLE_NO_ACCOUNT,
    // Any other Google Sign-In failure that isn't a user cancellation (cancellations are silent).
    GOOGLE_SIGN_IN_FAILED,
    // In-app "Connect Google account" (ADR-0051): the chosen Google account is already linked to a
    // different Love, Ipon account, so it can't be attached to this one.
    GOOGLE_ALREADY_LINKED,
    // In-app "Connect Google account" (ADR-0051): any other link failure that isn't a cancellation.
    GOOGLE_LINK_FAILED,
    NETWORK,
    UNKNOWN,
}

/** Thrown by auth use cases (validation) and the repository (mapped SDK failures). */
class AuthException(val error: AuthError) : Exception()
