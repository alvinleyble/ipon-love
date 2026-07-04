package com.iponlove.app.feature.auth.domain.model

/**
 * Whether someone is signed in, from the app's point of view. [Loading] covers the brief
 * window while the persisted session is restored on launch, so the gate can show a splash
 * instead of flashing the login screen.
 */
sealed interface AuthStatus {
    data object Loading : AuthStatus
    data object Unauthenticated : AuthStatus
    data class Authenticated(val userId: String) : AuthStatus

    /**
     * A session that exists only because the user tapped a password-recovery email link
     * (supabase-kt's `UserSession.type == "recovery"`) — never a normal sign-in. Routed to a
     * "set new password" screen, not the app shell (ADR-0027).
     */
    data class PasswordRecovery(val userId: String) : AuthStatus
}
