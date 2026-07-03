package com.iponlove.app.feature.onboarding.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Per-account onboarding bookkeeping (DataStore, never synced — ADR-0024). Both flags are
 * pure re-prompt suppressors, not the new-user signal itself; the signal is computed fresh
 * each launch by [com.iponlove.app.feature.onboarding.domain.usecase.ShouldShowOnboardingUseCase].
 * Reset by [com.iponlove.app.core.session.LocalDataWiper] on sign-out/account-switch so a
 * different account signing in on the same device still gets the guided starter-seeding step.
 */
interface OnboardingRepository {

    /** True once the signed-in account has completed or skipped the onboarding graph. */
    suspend fun isOnboardingDone(): Boolean

    suspend fun setOnboardingDone()

    /** True once the signed-in account has dismissed the unpaired-nudge card on Analysis home. */
    fun observePairingCardDismissed(): Flow<Boolean>

    suspend fun dismissPairingCard()

    /** Clears both flags — called on sign-out/account-switch, never mid-session. */
    suspend fun reset()
}
