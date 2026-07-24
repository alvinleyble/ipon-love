package com.iponlove.app.feature.auth.domain.repository

import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.model.LinkedIdentity
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

    /**
     * Signs in (or, via Supabase's automatic linking, into an existing verified account) with a
     * Google ID token obtained on-device (ADR-0050). [nonce] is the raw nonce whose hash is
     * embedded in the token. A Google identity is pre-verified, so this yields a session directly —
     * no email-confirmation path. On success the SDK's session status flips and the app's normal
     * authenticated bootstrap (ADR-0013/0021/0024) runs unchanged, method-agnostic.
     */
    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String)

    /**
     * Links a Google identity to the already-signed-in account (ADR-0051), using a Google ID token
     * obtained on-device (same Credential Manager path as [signInWithGoogleIdToken]). Unlike
     * sign-in, this keeps the current user id — it attaches the identity, it does not switch
     * accounts — so no ADR-0021 purge runs. Throws [com.iponlove.app.feature.auth.domain.model
     * .AuthException] on failure (e.g. the account is already linked elsewhere). [nonce] is the raw
     * nonce whose hash is embedded in the token. Requires Supabase manual linking to be enabled.
     */
    suspend fun linkGoogleIdentity(idToken: String, nonce: String)

    /**
     * The Google identity currently linked to the signed-in account, or null if none is linked
     * (ADR-0051). Read from the in-memory session's identity list; callers refresh the session
     * first if they need it post-link-or-elsewhere-fresh.
     */
    suspend fun linkedGoogleIdentity(): LinkedIdentity?

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

    /**
     * Requests an email change on the current session. Supabase sends a confirmation link to the
     * new address; the change is not live (and the synced users row email, ADR-0013, does not
     * follow) until that link is clicked.
     */
    suspend fun updateEmail(newEmail: String)

    /**
     * Re-fetches the current user from the server and updates the in-memory session, so a
     * server-confirmed change (e.g. a completed email change, Item 8) is reflected without an app
     * restart. Best-effort by convention — callers wrap it in `runCatching` (offline is expected).
     */
    suspend fun refreshCurrentUser()
}
