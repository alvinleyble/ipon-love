package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.auth.domain.model.AuthError
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.settings.domain.repository.ResetFinancesRepository
import com.iponlove.app.feature.user.domain.usecase.GetAccountEmailUseCase
import javax.inject.Inject

/**
 * Password re-auth (ADR-0037 decision 6) gates the wipe: [password] is checked against the
 * signed-in account before anything is touched, mirroring
 * [com.iponlove.app.feature.applock.domain.usecase.ResetPinAfterReAuthUseCase]'s re-auth-then-act
 * shape. Throws [AuthException] on a wrong password — nothing is deleted.
 */
class ResetFinancesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val getAccountEmail: GetAccountEmailUseCase,
    private val repository: ResetFinancesRepository,
) {
    suspend operator fun invoke(password: String) {
        val email = getAccountEmail() ?: throw AuthException(AuthError.UNKNOWN)
        authRepository.signIn(email, password)
        repository.reset()
    }
}
