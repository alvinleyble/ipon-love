package com.iponlove.app.feature.auth.domain.repository

import com.iponlove.app.feature.auth.domain.model.AuthStatus
import kotlinx.coroutines.flow.Flow

/** Outcome of a sign-up: whether the user can proceed or must confirm their email first. */
enum class SignUpResult { CONFIRMATION_REQUIRED, SIGNED_IN }

/**
 * Email + password auth over Supabase (v1). All calls throw
 * [com.iponlove.app.feature.auth.domain.model.AuthException] on failure.
 */
interface AuthRepository {

    /** Live auth state, driven by the SDK's session status (restore, refresh, sign-out). */
    val status: Flow<AuthStatus>

    /** [name] is carried into Supabase auth `user_metadata` as `display_name` (ADR-0016). */
    suspend fun signUp(name: String, email: String, password: String): SignUpResult

    suspend fun signIn(email: String, password: String)

    suspend fun signOut()

    /**
     * Clears only the local session (no server round-trip), flipping [status] to
     * Unauthenticated. Used after account deletion (ADR-0045): the server user and its sessions
     * are already gone via cascade, so a normal [signOut] server revoke would be a doomed
     * round-trip — this just drops the local token so the app returns to the auth gate.
     */
    suspend fun clearLocalSession()

    /** Sends Supabase's password-recovery email; the link lands back on the app's deep link. */
    suspend fun sendPasswordReset(email: String)

    /** Sets a new password on the current (recovery) session. */
    suspend fun updatePassword(newPassword: String)
}
