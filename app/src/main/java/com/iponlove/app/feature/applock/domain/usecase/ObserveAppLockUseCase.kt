package com.iponlove.app.feature.applock.domain.usecase

import com.iponlove.app.feature.applock.domain.model.AppLockPreferences
import com.iponlove.app.feature.applock.domain.repository.AppLockRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAppLockUseCase @Inject constructor(private val repo: AppLockRepository) {
    operator fun invoke(): Flow<AppLockPreferences> = repo.observe()
}
