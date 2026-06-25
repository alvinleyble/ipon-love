package com.iponlove.app.feature.applock.domain.usecase

import com.iponlove.app.feature.applock.domain.repository.AppLockRepository
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

class ResetPinAfterReAuthUseCase @Inject constructor(
    private val repo: AppLockRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String) {
        authRepository.signIn(email, password)
        repo.clearPin()
    }
}
