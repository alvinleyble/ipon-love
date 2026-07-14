package com.iponlove.app.feature.settings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.feature.auth.domain.model.AuthError
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.auth.domain.repository.SignUpResult
import com.iponlove.app.feature.settings.domain.repository.AccountDeletionRepository
import com.iponlove.app.feature.settings.domain.usecase.DeleteUserAccountUseCase
import com.iponlove.app.feature.user.domain.usecase.GetAccountEmailUseCase
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The re-auth gate (ADR-0045): a wrong password must throw before the destructive
 * [AccountDeletionRepository.deleteAccount] is ever reached — nothing is deleted. Mirrors
 * [ResetFinancesUseCaseTest].
 */
class DeleteUserAccountUseCaseTest {

    private suspend fun errorFrom(block: suspend () -> Unit): AuthError = try {
        block()
        error("expected AuthException")
    } catch (e: AuthException) {
        e.error
    }

    private val authRepository = FakeAuthRepositoryForDelete()
    private val getAccountEmail = GetAccountEmailUseCase(
        object : CurrentUserProvider {
            override fun userId() = "user-1"
            override fun email() = "hubby@iponlove.com"
        },
    )
    private val deletionRepository = FakeAccountDeletionRepository()
    private val useCase = DeleteUserAccountUseCase(authRepository, getAccountEmail, deletionRepository)

    @Test
    fun invoke_correctPassword_reAuthsThenDeletes() = runTest {
        authRepository.expectedPassword = "correct-password"

        useCase("correct-password")

        assertThat(authRepository.signInCalledWith).isEqualTo("hubby@iponlove.com" to "correct-password")
        assertThat(deletionRepository.deleteCalled).isTrue()
    }

    @Test
    fun invoke_wrongPassword_throwsAndNeverDeletes() = runTest {
        authRepository.expectedPassword = "correct-password"

        val error = errorFrom { useCase("wrong-password") }

        assertThat(error).isEqualTo(AuthError.INVALID_CREDENTIALS)
        assertThat(deletionRepository.deleteCalled).isFalse()
    }
}

private class FakeAuthRepositoryForDelete : AuthRepository {
    var expectedPassword: String? = null
    var signInCalledWith: Pair<String, String>? = null

    override val status = emptyFlow<AuthStatus>()

    override suspend fun signUp(name: String, email: String, password: String): SignUpResult =
        error("not used by this test")

    override suspend fun signIn(email: String, password: String) {
        signInCalledWith = email to password
        if (password != expectedPassword) throw AuthException(AuthError.INVALID_CREDENTIALS)
    }

    override suspend fun signOut() = error("not used by this test")
    override suspend fun clearLocalSession() = error("not used by this test")
    override suspend fun sendPasswordReset(email: String) = error("not used by this test")
    override suspend fun updatePassword(newPassword: String) = error("not used by this test")
    override suspend fun updateEmail(newEmail: String) = error("not used by this test")
    override suspend fun refreshCurrentUser() = error("not used by this test")
}

private class FakeAccountDeletionRepository : AccountDeletionRepository {
    var deleteCalled = false
    override suspend fun deleteAccount() {
        deleteCalled = true
    }
}
