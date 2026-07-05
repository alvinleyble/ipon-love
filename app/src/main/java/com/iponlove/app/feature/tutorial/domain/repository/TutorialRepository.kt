package com.iponlove.app.feature.tutorial.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Onboarding-tour bookkeeping (ADR-0038). A single local-only DataStore entry — a **set of seen
 * tour IDs** — deliberately **not** synced and **not** wiped by
 * [com.iponlove.app.core.session.LocalDataWiper] on sign-out/account-switch.
 *
 * Because it lives purely in local storage, the set resets for free exactly when onboarding should
 * fire again — a genuinely new install, or an existing user whose local data was cleared/reinstalled
 * (whose real rows exist on the server, so the gate deliberately skips a fresh onboarding of *data*
 * but still re-teaches *where things are*). It intentionally does *not* reset on a plain
 * sign-out/sign-in, so re-logging in on the same install (the common beta-tester loop) never
 * re-triggers the tours.
 *
 * **Legacy migration:** the v1.6.1 single-boolean `tutorial_seen` (ADR-0034) is honored on read —
 * a `true` there seeds the set with the shell tour ID so existing testers don't re-see the shell
 * walkthrough, while every new module tour still fires on first visit.
 */
interface TutorialRepository {

    /** The set of tour IDs already completed or skipped on this install (legacy flag applied). */
    fun observeSeenTours(): Flow<Set<String>>

    /** Record that [tourId] has been completed or skipped, so its gate won't re-fire. */
    suspend fun markTourSeen(tourId: String)

    /** Clear every seen tour ID — the "Replay tutorial" reset; re-arms all tours. */
    suspend fun clearAllTours()
}
