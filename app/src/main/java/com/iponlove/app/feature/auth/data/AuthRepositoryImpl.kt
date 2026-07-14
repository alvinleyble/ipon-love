package com.iponlove.app.feature.auth.data

import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.auth.domain.repository.SignUpResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase-backed [AuthRepository]. Translates the SDK's session status into the app's
 * [AuthStatus] and its thrown failures into typed [AuthException]s. The SDK owns session
 * persistence and token refresh; this just adapts it to the domain.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val client: SupabaseClient,
) : AuthRepository {

    override val status: Flow<AuthStatus> = client.auth.sessionStatus.map { s ->
        when (s) {
            is SessionStatus.Authenticated -> {
                val userId = s.session.user?.id.orEmpty()
                // A password-recovery deep link authenticates identically to a normal sign-in;
                // `session.type == "recovery"` is the SDK's only signal that this session exists
                // solely to let the user set a new password (ADR-0027) — never fold it into a
                // normal Authenticated status.
                if (s.session.type == "recovery") AuthStatus.PasswordRecovery(userId)
                else AuthStatus.Authenticated(userId)
            }
            is SessionStatus.NotAuthenticated -> AuthStatus.Unauthenticated
            is SessionStatus.Initializing -> AuthStatus.Loading
            is SessionStatus.RefreshFailure -> AuthStatus.Unauthenticated
        }
    }

    override suspend fun signUp(name: String, email: String, password: String): SignUpResult = mapErrors {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            // The name must survive the gap between sign-up and first login (possibly on
            // another device), so it rides on the auth account itself, not local storage.
            // EnsureCurrentUserRowUseCase reads it back to seed the users row (ADR-0016).
            this.data = buildJsonObject { put("display_name", name) }
        }
        // With email confirmation on, sign-up creates no session until the link is clicked.
        if (client.auth.currentSessionOrNull() != null) SignUpResult.SIGNED_IN
        else SignUpResult.CONFIRMATION_REQUIRED
    }

    override suspend fun signIn(email: String, password: String) = mapErrors {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() = mapErrors { client.auth.signOut() }

    override suspend fun clearLocalSession() = mapErrors { client.auth.clearSession() }

    override suspend fun sendPasswordReset(email: String) = mapErrors {
        client.auth.resetPasswordForEmail(email, redirectUrl = PASSWORD_RECOVERY_REDIRECT_URL)
    }

    override suspend fun updatePassword(newPassword: String) = mapErrors {
        client.auth.updateUser { password = newPassword }
        Unit
    }

    override suspend fun updateEmail(newEmail: String) = mapErrors {
        // Supabase emails a confirmation link to the new address; the session's email only flips
        // once it's clicked (the "Secure email change" flow). No local state changes here.
        client.auth.updateUser { email = newEmail }
        Unit
    }

    override suspend fun refreshCurrentUser() = mapErrors {
        // Pulls the latest user (GET /auth/v1/user) and writes it back into the local session, so
        // a completed email change surfaces without a restart. With "Secure email change" on, this
        // still returns the old email until *both* confirmations land — exactly the desired
        // behavior (the UI only shows the new address once the change is actually applied).
        client.auth.retrieveUserForCurrentSession(updateSession = true)
        Unit
    }

    /** Run an auth SDK call, translating its failures to a typed [AuthException]. */
    private suspend inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: AuthException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("AuthRepositoryImpl", "auth call failed", e)
        throw AuthException(AuthErrorClassifier.classify(e.message))
    }

    private companion object {
        // Same deep link the email-confirmation flow already uses (AndroidManifest.xml); the
        // Supabase dashboard's reset-password template must be configured to redirect here too.
        const val PASSWORD_RECOVERY_REDIRECT_URL = "com.iponlove.app://login-callback"
    }
}
