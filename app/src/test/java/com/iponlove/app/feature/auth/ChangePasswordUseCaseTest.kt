package com.iponlove.app.feature.auth

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.feature.auth.domain.model.AuthError
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.auth.domain.repository.SignUpResult
import com.iponlove.app.feature.auth.domain.usecase.ChangePasswordUseCase
import com.iponlove.app.feature.user.domain.usecase.GetAccountEmailUseCase
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChangePasswordUseCaseTest {

    private val auth = FakeAuth().apply { expectedPassword = "OldPass1!" }
    private val getEmail = GetAccountEmailUseCase(
        object : CurrentUserProvider {
            override fun userId() = "user-1"
            override fun email() = "me@iponlove.com"
        },
    )
    private val useCase = ChangePasswordUseCase(auth, getEmail)

    private suspend fun errorFrom(block: suspend () -> Unit): AuthError = try {
        block(); error("expected AuthException")
    } catch (e: AuthException) {
        e.error
    }

    @Test
    fun valid_reAuthsThenUpdatesPassword() = runTest {
        useCase("OldPass1!", "NewPass1!", "NewPass1!")

        assertThat(auth.signInCalledWith).isEqualTo("me@iponlove.com" to "OldPass1!")
        assertThat(auth.updatedPassword).isEqualTo("NewPass1!")
    }

    @Test
    fun wrongCurrentPassword_throwsAndNeverUpdates() = runTest {
        val error = errorFrom { useCase("WrongPass1!", "NewPass1!", "NewPass1!") }

        assertThat(error).isEqualTo(AuthError.INVALID_CREDENTIALS)
        assertThat(auth.updatedPassword).isNull()
    }

    @Test
    fun weakNewPassword_failsBeforeReAuth() = runTest {
        val error = errorFrom { useCase("OldPass1!", "weak", "weak") }

        assertThat(error).isEqualTo(AuthError.WEAK_PASSWORD)
        assertThat(auth.signInCalledWith).isNull()
    }

    @Test
    fun mismatchedConfirmation_failsBeforeReAuth() = runTest {
        val error = errorFrom { useCase("OldPass1!", "NewPass1!", "Different1!") }

        assertThat(error).isEqualTo(AuthError.PASSWORD_MISMATCH)
        assertThat(auth.signInCalledWith).isNull()
    }

    @Test
    fun sameAsCurrent_failsBeforeReAuth() = runTest {
        val error = errorFrom { useCase("OldPass1!", "OldPass1!", "OldPass1!") }

        assertThat(error).isEqualTo(AuthError.SAME_AS_OLD_PASSWORD)
        assertThat(auth.signInCalledWith).isNull()
    }
}

private class FakeAuth : AuthRepository {
    var expectedPassword: String? = null
    var signInCalledWith: Pair<String, String>? = null
    var updatedPassword: String? = null
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
    override suspend fun updatePassword(newPassword: String) { updatedPassword = newPassword }
    override suspend fun updateEmail(newEmail: String) { updatedEmail = newEmail }
    override suspend fun refreshCurrentUser() = error("unused")
    override suspend fun linkGoogleIdentity(idToken: String, nonce: String) = error("unused")
    override suspend fun linkedGoogleIdentity(): com.iponlove.app.feature.auth.domain.model.LinkedIdentity? = null
}
