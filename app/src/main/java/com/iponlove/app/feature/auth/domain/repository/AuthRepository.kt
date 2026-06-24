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

    suspend fun signUp(email: String, password: String): SignUpResult

    suspend fun signIn(email: String, password: String)

    suspend fun signOut()
}
