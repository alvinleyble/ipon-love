package com.iponlove.app.feature.auth.data

import com.iponlove.app.feature.auth.domain.model.AuthError

/**
 * Maps a failed auth call's message to a user-facing [AuthError]. The Supabase SDK surfaces
 * GoTrue failures as exceptions whose messages carry the server's error text/code; we match
 * on those substrings rather than depending on the SDK's exception class hierarchy. Pure —
 * unit-tested.
 */
internal object AuthErrorClassifier {

    fun classify(message: String?): AuthError {
        val msg = message?.lowercase().orEmpty()
        return when {
            "not confirmed" in msg -> AuthError.EMAIL_NOT_CONFIRMED
            "already registered" in msg || "already been registered" in msg ||
                "user already exists" in msg || "email_exists" in msg -> AuthError.EMAIL_ALREADY_REGISTERED
            "invalid login" in msg || "invalid credentials" in msg -> AuthError.INVALID_CREDENTIALS
            "password should be" in msg || "weak password" in msg -> AuthError.WEAK_PASSWORD
            "invalid email" in msg || "unable to validate email" in msg -> AuthError.INVALID_EMAIL
            "host" in msg || "connect" in msg || "timeout" in msg ||
                "network" in msg || "unreachable" in msg -> AuthError.NETWORK
            else -> AuthError.UNKNOWN
        }
    }
}
