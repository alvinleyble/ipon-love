package com.iponlove.app.core.session

import com.iponlove.app.core.database.IponDatabase
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.data.SyncStatusStore
import com.iponlove.app.feature.applock.domain.repository.AppLockRepository
import com.iponlove.app.feature.onboarding.domain.repository.OnboardingRepository
import com.iponlove.app.feature.widget.presentation.WidgetRefresher
import com.iponlove.app.navigation.NavConfigRepository
import javax.inject.Inject

/**
 * Tears down all account-scoped local state so one user's offline-first data can never surface
 * under another's session (ADR-0021). Room is a process [javax.inject.Singleton] that is never
 * recreated across a sign-out, so without this the next account would read the previous one's
 * rows — including couple-owned rows that the `(userId = :me OR coupleId IS NOT NULL)` DAO
 * filters surface regardless of owner.
 *
 * Wipes five things together — they are meaningless apart:
 *  - the sync pull cursors FIRST (so a re-login re-pulls from `server_rev = 0` instead of
 *    skipping everything below a stale cursor). Ordering invariant: cursors must reset before
 *    Room clears. A crash after cursor reset leaves cursor=0 + stale Room, which self-heals on
 *    the next pull; a crash after Room clears but before cursor reset leaves empty Room + high
 *    cursors, which permanently wedges the couples/partner pulls (`server_rev > cursor` never
 *    matches) — the app then shows "not paired" while the server still has the couple.
 *  - Room (the financial/notes source of truth),
 *  - the nav layout (a per-device UI preference the next account should start fresh on),
 *  - the onboarding re-prompt flags (ADR-0024 addendum) — without this, a second real account
 *    signing in on a device that already onboarded once skips the graph entirely, including
 *    the starter-template seeding step, and lands in an empty app with no accounts/categories,
 *  - the app-lock PIN + biometric flag — the local lock is set per account, so leaving the
 *    previous user's PIN behind would lock the incoming account out behind a code it never set
 *    (and, conversely, expose the switch as a data-isolation gap). Cleared here so the next
 *    account starts with no lock and can set its own.
 *  - the last-synced timestamp (Item 9) — it describes the previous account's sync history,
 *    so the next account's Settings must not read "Last synced X ago" off it.
 *
 * Does NOT touch [LastActiveUserStore]: that is the guard's own bookkeeping and must outlive
 * the wipe. The Supabase session is cleared separately by the auth layer.
 */
class LocalDataWiper @Inject constructor(
    private val database: IponDatabase,
    private val cursors: SyncCursorStore,
    private val navConfig: NavConfigRepository,
    private val onboarding: OnboardingRepository,
    private val appLock: AppLockRepository,
    private val syncStatus: SyncStatusStore,
    private val widgetRefresher: WidgetRefresher,
) {
    suspend fun wipe() {
        cursors.reset()
        database.clearAll()
        navConfig.reset()
        onboarding.reset()
        appLock.clearPin()
        syncStatus.clear()
        // Repaint the home-screen widgets off the now-empty state, HERE and not only in
        // MainActivity's (cancellable) composition: without this, the balance widget kept showing
        // the previous account's figures until the next account's first sync finished — a
        // cross-account leak on sign-out / account-switch / delete-account (Alvin, 2026-07-14).
        // Best-effort: a repaint failure must never fail the wipe (whose partial-run invariants
        // actually matter — see the ordering note above).
        runCatching { widgetRefresher.refresh() }
    }
}
