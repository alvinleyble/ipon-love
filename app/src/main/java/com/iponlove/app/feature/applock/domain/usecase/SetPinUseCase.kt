package com.iponlove.app.feature.applock.domain.usecase

import com.iponlove.app.feature.applock.domain.repository.AppLockRepository
import javax.inject.Inject

class SetPinUseCase @Inject constructor(private val repo: AppLockRepository) {
    suspend operator fun invoke(rawPin: String) = repo.setPin(rawPin)
}
