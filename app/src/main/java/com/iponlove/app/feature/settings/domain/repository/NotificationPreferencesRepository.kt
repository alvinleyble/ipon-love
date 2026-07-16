package com.iponlove.app.feature.settings.domain.repository

import kotlinx.coroutines.flow.Flow

/** Local-only — mirrors [PrivacyModeRepository]'s shape. Defaults to enabled when unset. */
interface NotificationPreferencesRepository {
    fun observeBudgetAlertsEnabled(): Flow<Boolean>
    suspend fun setBudgetAlertsEnabled(enabled: Boolean)
}
