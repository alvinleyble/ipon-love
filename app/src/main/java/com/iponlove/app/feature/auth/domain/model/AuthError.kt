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
    NETWORK,
    UNKNOWN,
}

/** Thrown by auth use cases (validation) and the repository (mapped SDK failures). */
class AuthException(val error: AuthError) : Exception()
