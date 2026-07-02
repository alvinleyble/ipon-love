package com.iponlove.app.feature.onboarding.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Device-local onboarding bookkeeping (DataStore, never synced — ADR-0024). Both flags are
 * pure re-prompt suppressors, not the new-user signal itself; the signal is computed fresh
 * each launch by [com.iponlove.app.feature.onboarding.domain.usecase.ShouldShowOnboardingUseCase].
 */
interface OnboardingRepository {

    /** True once this device has completed or skipped the onboarding graph. */
    suspend fun isOnboardingDone(): Boolean

    suspend fun setOnboardingDone()

    /** True once this device has dismissed the unpaired-nudge card on Analysis home. */
    fun observePairingCardDismissed(): Flow<Boolean>

    suspend fun dismissPairingCard()
}
