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
            // GoTrue's own updateUser check: the recovery flow's new password matches the
            // account's current password. Raw error code "same_password" — must be checked
            // before the "password should be" match below, since GoTrue's same-password message
            // ("...should be different from the old password") also contains that substring.
            "same_password" in msg -> AuthError.SAME_AS_OLD_PASSWORD
            // "password should be" covers length rejections; "password should contain" is
            // GoTrue's character-class policy message (e.g. requires upper/lowercase, a digit,
            // and a symbol) — the exception's message also ends with the raw error code
            // "weak_password" (underscore, not "weak password"), which the old check missed.
            "password should be" in msg || "password should contain" in msg ||
                "weak_password" in msg -> AuthError.WEAK_PASSWORD
            "invalid email" in msg || "unable to validate email" in msg -> AuthError.INVALID_EMAIL
            // GoTrue's default email-rate-limit message, e.g. "For security purposes, you can
            // only request this after 46 seconds." Surfaced by resetPasswordForEmail if it's
            // called again too soon for the same address.
            "security purposes" in msg || "rate limit" in msg -> AuthError.RATE_LIMITED
            "host" in msg || "connect" in msg || "timeout" in msg ||
                "network" in msg || "unreachable" in msg -> AuthError.NETWORK
            else -> AuthError.UNKNOWN
        }
    }
}
