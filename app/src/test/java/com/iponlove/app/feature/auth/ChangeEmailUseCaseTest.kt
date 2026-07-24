package com.iponlove.app.feature.auth

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.feature.auth.domain.model.AuthError
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.auth.domain.repository.SignUpResult
import com.iponlove.app.feature.auth.domain.usecase.ChangeEmailUseCase
import com.iponlove.app.feature.user.domain.usecase.GetAccountEmailUseCase
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChangeEmailUseCaseTest {

    private val auth = FakeAuthForEmail().apply { expectedPassword = "OldPass1!" }
    private val getEmail = GetAccountEmailUseCase(
        object : CurrentUserProvider {
            override fun userId() = "user-1"
            override fun email() = "me@iponlove.com"
        },
    )
    private val useCase = ChangeEmailUseCase(auth, getEmail)

    private suspend fun errorFrom(block: suspend () -> Unit): AuthError = try {
        block(); error("expected AuthException")
    } catch (e: AuthException) {
        e.error
    }

    @Test
    fun valid_reAuthsThenRequestsChange_trimmed() = runTest {
        useCase("OldPass1!", "  new@iponlove.com  ")

        assertThat(auth.signInCalledWith).isEqualTo("me@iponlove.com" to "OldPass1!")
        assertThat(auth.updatedEmail).isEqualTo("new@iponlove.com")
    }

    @Test
    fun invalidEmail_failsBeforeReAuth() = runTest {
        val error = errorFrom { useCase("OldPass1!", "not-an-email") }

        assertThat(error).isEqualTo(AuthError.INVALID_EMAIL)
        assertThat(auth.signInCalledWith).isNull()
    }

    @Test
    fun sameEmailIgnoringCase_rejectedBeforeReAuth() = runTest {
        val error = errorFrom { useCase("OldPass1!", "ME@iponlove.com") }

        assertThat(error).isEqualTo(AuthError.INVALID_EMAIL)
        assertThat(auth.signInCalledWith).isNull()
    }

    @Test
    fun wrongPassword_throwsAndNeverRequestsChange() = runTest {
        val error = errorFrom { useCase("WrongPass1!", "new@iponlove.com") }

        assertThat(error).isEqualTo(AuthError.INVALID_CREDENTIALS)
        assertThat(auth.updatedEmail).isNull()
    }
}

private class FakeAuthForEmail : AuthRepository {
    var expectedPassword: String? = null
    var signInCalledWith: Pair<String, String>? = null
    var updatedEmail: String? = null

    override val status = emptyFlow<AuthStatus>()
    override suspend fun signUp(name: String, email: String, password: String): SignUpResult =
        error("unused")

    override suspend fun signIn(email: String, password: String) {
        signInCalledWith = email to password
        if (password != expectedPassword) throw AuthException(AuthError.INVALID_CREDENTIALS)
    }

    override suspend fun signInWithGoogleIdToken(idToken: String, nonce: String) = Unit

    override suspend fun signOut() = error("unused")
    override suspend fun clearLocalSession() = error("unused")
    override suspend fun sendPasswordReset(email: String) = error("unused")
    override suspend fun updatePassword(newPassword: String) = error("unused")
    override suspend fun updateEmail(newEmail: String) { updatedEmail = newEmail }
    override suspend fun refreshCurrentUser() = error("unused")
}
