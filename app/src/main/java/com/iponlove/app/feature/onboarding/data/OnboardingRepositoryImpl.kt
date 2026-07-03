package com.iponlove.app.feature.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    override suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_PAIRING_CARD_DISMISSED = booleanPreferencesKey("pairing_card_dismissed")
    }
}
