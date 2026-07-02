package com.iponlove.app.feature.applock.domain.repository

import com.iponlove.app.feature.applock.domain.model.AppLockPreferences
import kotlinx.coroutines.flow.Flow

interface AppLockRepository {
    fun observe(): Flow<AppLockPreferences>
    suspend fun setPin(rawPin: String)
    suspend fun verifyPin(rawPin: String): Boolean
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun markBiometricNudgeShown()
    suspend fun clearPin()
}
