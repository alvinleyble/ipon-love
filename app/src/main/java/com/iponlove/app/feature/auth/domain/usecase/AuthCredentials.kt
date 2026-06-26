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

    fun validatePassword(password: String) {
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw AuthException(AuthError.WEAK_PASSWORD)
        }
    }
}
