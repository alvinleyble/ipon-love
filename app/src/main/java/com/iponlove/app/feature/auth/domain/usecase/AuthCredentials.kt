package com.iponlove.app.feature.auth.domain.usecase

import com.iponlove.app.feature.auth.domain.model.AuthError
import com.iponlove.app.feature.auth.domain.model.AuthException

/**
 * Shared client-side credential checks, so the use cases fail fast on obviously bad input
 * before a network round-trip. Pure — unit-tested. Supabase's own server-side rules remain
 * authoritative; these just catch the common cases early.
 */
internal object AuthCredentials {

    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_NAME_LENGTH = 50

    /**
     * The display name shown to a partner (combined view, shared-note attribution). Required at
     * registration: trimmed, non-blank, and at most [MAX_NAME_LENGTH] chars (ADR-0016).
     */
    fun validateName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) {
            throw AuthException(AuthError.INVALID_NAME)
        }
    }

    fun validateEmail(email: String) {
        // Intentionally loose — "contains an @ with text on both sides". The server does the
        // real validation; this only blocks empty/obviously-wrong input.
        val trimmed = email.trim()
        val at = trimmed.indexOf('@')
        if (at <= 0 || at == trimmed.length - 1 || trimmed.contains(' ')) {
            throw AuthException(AuthError.INVALID_EMAIL)
        }
    }

    /**
     * Mirrors this Supabase project's actual password-strength policy (configured server-side,
     * not a client choice) — length alone isn't enough; the server also rejects a password
     * missing any of these character classes with `weak_password`. Checking it here avoids a
     * round trip for the exact rejection the server would give anyway.
     */
    fun validatePassword(password: String) {
        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        if (password.length < MIN_PASSWORD_LENGTH || !hasLower || !hasUpper || !hasDigit || !hasSymbol) {
            throw AuthException(AuthError.WEAK_PASSWORD)
        }
    }

    /** A typo here costs a whole new recovery-email round trip, so both entries must agree. */
    fun validatePasswordsMatch(password: String, confirmPassword: String) {
        if (password != confirmPassword) {
            throw AuthException(AuthError.PASSWORD_MISMATCH)
        }
    }
}
