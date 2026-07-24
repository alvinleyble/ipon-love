package com.iponlove.app.feature.settings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.feature.auth.domain.model.AuthError
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.auth.domain.repository.SignUpResult
import com.iponlove.app.feature.settings.domain.model.ResetFinancesCounts
import com.iponlove.app.feature.settings.domain.repository.ResetFinancesRepository
import com.iponlove.app.feature.settings.domain.usecase.ResetFinancesUseCase
import com.iponlove.app.feature.user.domain.usecase.GetAccountEmailUseCase
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResetFinancesUseCaseTest {

    private suspend fun errorFrom(block: suspend () -> Unit): AuthError {
        return try {
            block()
            error("expected AuthException")
        } catch (e: AuthException) {
            e.error
        }
    }

    private val authRepository = FakeAuthRepository()
    private val getAccountEmail = GetAccountEmailUseCase(
        object : CurrentUserProvider {
            override fun userId() = "user-1"
            override fun email() = "wifey@iponlove.com"
        },
    )
    private val resetFinancesRepository = FakeResetFinancesRepository()
    private val useCase = ResetFinancesUseCase(authRepository, getAccountEmail, resetFinancesRepository)

    @Test
    fun invoke_correctPassword_signsInThenResets() = runTest {
        authRepository.expectedPassword = "correct-password"

        useCase("correct-password")

        assertThat(authRepository.signInCalledWith).isEqualTo("wifey@iponlove.com" to "correct-password")
        assertThat(resetFinancesRepository.resetCalled).isTrue()
    }

    @Test
    fun invoke_wrongPassword_throwsAndNeverResets() = runTest {
        authRepository.expectedPassword = "correct-password"

        val error = errorFrom { useCase("wrong-password") }

        assertThat(error).isEqualTo(AuthError.INVALID_CREDENTIALS)
        assertThat(resetFinancesRepository.resetCalled).isFalse()
    }
}

private class FakeAuthRepository : AuthRepository {
    var expectedPassword: String? = null
    var signInCalledWith: Pair<String, String>? = null

    override val status = emptyFlow<AuthStatus>()

    override suspend fun signUp(name: String, email: String, password: String): SignUpResult =
        error("not used by this test")

    override suspend fun signIn(email: String, password: String) {
        signInCalledWith = email to password
        if (password != expectedPassword) throw AuthException(AuthError.INVALID_CREDENTIALS)
    }

    override suspend fun signInWithGoogleIdToken(idToken: String, nonce: String) = Unit

    override suspend fun signOut() = error("not used by this test")
    override suspend fun clearLocalSession() = error("not used by this test")
    override suspend fun sendPasswordReset(email: String) = error("not used by this test")
    override suspend fun updatePassword(newPassword: String) = error("not used by this test")
    override suspend fun updateEmail(newEmail: String) = error("not used by this test")
    override suspend fun refreshCurrentUser() = error("not used by this test")
    override suspend fun linkGoogleIdentity(idToken: String, nonce: String) = error("not used by this test")
    override suspend fun linkedGoogleIdentity(): com.iponlove.app.feature.auth.domain.model.LinkedIdentity? = null
}

private class FakeResetFinancesRepository : ResetFinancesRepository {
    var resetCalled = false

    override suspend fun previewCounts(): ResetFinancesCounts =
        ResetFinancesCounts(transactions = 0, accounts = 0)

    override suspend fun reset() {
        resetCalled = true
    }
}
