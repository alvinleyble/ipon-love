package com.iponlove.app.feature.auth.data

import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.auth.domain.repository.SignUpResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
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
            is SessionStatus.Authenticated -> AuthStatus.Authenticated(s.session.user?.id.orEmpty())
            is SessionStatus.NotAuthenticated -> AuthStatus.Unauthenticated
            is SessionStatus.Initializing -> AuthStatus.Loading
            is SessionStatus.RefreshFailure -> AuthStatus.Unauthenticated
        }
    }

    override suspend fun signUp(email: String, password: String): SignUpResult = mapErrors {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
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

    /** Run an auth SDK call, translating its failures to a typed [AuthException]. */
    private suspend inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: AuthException) {
        throw e
    } catch (e: Exception) {
        throw AuthException(AuthErrorClassifier.classify(e.message))
    }
}
