package com.iponlove.app.feature.settings.domain.repository

import kotlinx.coroutines.flow.Flow

interface PrivacyModeRepository {
    fun observe(): Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
}
