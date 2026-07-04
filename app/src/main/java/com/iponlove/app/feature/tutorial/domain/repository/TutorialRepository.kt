package com.iponlove.app.feature.tutorial.domain.repository

/**
 * First-run tutorial bookkeeping (ADR-0034). A single local-only DataStore flag — deliberately
 * **not** synced and **not** wiped by [com.iponlove.app.core.session.LocalDataWiper] on
 * sign-out/account-switch.
 *
 * Because it lives purely in local storage, the flag resets itself for free exactly when the
 * tutorial should fire again — a genuinely new install, or an existing user whose local data was
 * cleared/reinstalled (whose real rows exist on the server, so the onboarding gate deliberately
 * skips them). It intentionally does *not* reset on a plain sign-out/sign-in, so re-logging in on
 * the same install (the common beta-tester loop) never re-triggers the tour.
 */
interface TutorialRepository {

    /** True once the tutorial has been completed or skipped on this install. */
    suspend fun isTutorialSeen(): Boolean

    suspend fun setTutorialSeen()
}
