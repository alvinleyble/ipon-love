package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.auth.domain.model.AuthError
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.settings.domain.repository.AccountDeletionRepository
import com.iponlove.app.feature.user.domain.usecase.GetAccountEmailUseCase
import javax.inject.Inject

/**
 * Password re-auth (ADR-0045) gates the delete: [password] is verified against the signed-in
 * account before anything is destroyed, mirroring [ResetFinancesUseCase]. Throws [AuthException]
 * on a wrong password — nothing is deleted (the RPC is never reached). The re-auth doubles as a
 * fresh session refresh right before the destructive call.
 *
 * Named to dodge the existing *financial* `DeleteAccountUseCase` (feature/accounts).
 */
class DeleteUserAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val getAccountEmail: GetAccountEmailUseCase,
    private val repository: AccountDeletionRepository,
) {
    suspend operator fun invoke(password: String) {
        val email = getAccountEmail() ?: throw AuthException(AuthError.UNKNOWN)
        authRepository.signIn(email, password)
        repository.deleteAccount()
    }
}
