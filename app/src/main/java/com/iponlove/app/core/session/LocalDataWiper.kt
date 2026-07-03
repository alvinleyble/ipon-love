package com.iponlove.app.core.session

import com.iponlove.app.core.database.IponDatabase
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.feature.onboarding.domain.repository.OnboardingRepository
import com.iponlove.app.navigation.NavConfigRepository
import javax.inject.Inject

/**
 * Tears down all account-scoped local state so one user's offline-first data can never surface
 * under another's session (ADR-0021). Room is a process [javax.inject.Singleton] that is never
 * recreated across a sign-out, so without this the next account would read the previous one's
 * rows — including couple-owned rows that the `(userId = :me OR coupleId IS NOT NULL)` DAO
 * filters surface regardless of owner.
 *
 * Wipes four things together — they are meaningless apart:
 *  - Room (the financial/notes source of truth),
 *  - the sync pull cursors (so a re-login re-pulls from `server_rev = 0` instead of skipping
 *    everything below a stale cursor),
 *  - the nav layout (a per-device UI preference the next account should start fresh on),
 *  - the onboarding re-prompt flags (ADR-0024 addendum) — without this, a second real account
 *    signing in on a device that already onboarded once skips the graph entirely, including
 *    the starter-template seeding step, and lands in an empty app with no accounts/categories.
 *
 * Does NOT touch [LastActiveUserStore]: that is the guard's own bookkeeping and must outlive
 * the wipe. The Supabase session is cleared separately by the auth layer.
 */
class LocalDataWiper @Inject constructor(
    private val database: IponDatabase,
    private val cursors: SyncCursorStore,
    private val navConfig: NavConfigRepository,
    private val onboarding: OnboardingRepository,
) {
    suspend fun wipe() {
        database.clearAll()
        cursors.reset()
        navConfig.reset()
        onboarding.reset()
    }
}
