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
}
