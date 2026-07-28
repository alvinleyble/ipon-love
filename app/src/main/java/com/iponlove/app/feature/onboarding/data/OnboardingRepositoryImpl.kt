package com.iponlove.app.feature.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.iponlove.app.feature.onboarding.di.OnboardingDataStore
import com.iponlove.app.feature.onboarding.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OnboardingRepositoryImpl @Inject constructor(
    @OnboardingDataStore private val dataStore: DataStore<Preferences>,
) : OnboardingRepository {

    override suspend fun isOnboardingDone(): Boolean =
        dataStore.data.first()[KEY_ONBOARDING_DONE] ?: false

    override suspend fun setOnboardingDone() {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    override fun observePairingCardDismissed(): Flow<Boolean> =
        dataStore.data.map { it[KEY_PAIRING_CARD_DISMISSED] ?: false }

    override suspend fun dismissPairingCard() {
        dataStore.edit { it[KEY_PAIRING_CARD_DISMISSED] = true }
    }

    override fun observeWidgetNudgeLastShownAt(): Flow<Long?> =
        dataStore.data.map { it[KEY_WIDGET_NUDGE_LAST_SHOWN_AT] }

    override suspend fun recordWidgetNudgeShown() {
        dataStore.edit { it[KEY_WIDGET_NUDGE_LAST_SHOWN_AT] = System.currentTimeMillis() }
    }

    override fun observeComingUpCollapsed(): Flow<Boolean> =
        dataStore.data.map { it[KEY_COMING_UP_COLLAPSED] ?: false }

    override suspend fun setComingUpCollapsed(collapsed: Boolean) {
        dataStore.edit { it[KEY_COMING_UP_COLLAPSED] = collapsed }
    }

    override suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_PAIRING_CARD_DISMISSED = booleanPreferencesKey("pairing_card_dismissed")
        val KEY_WIDGET_NUDGE_LAST_SHOWN_AT = longPreferencesKey("widget_nudge_last_shown_at")
        val KEY_COMING_UP_COLLAPSED = booleanPreferencesKey("coming_up_collapsed")
    }
}
