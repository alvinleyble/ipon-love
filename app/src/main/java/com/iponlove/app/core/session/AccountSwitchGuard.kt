package com.iponlove.app.core.session

import javax.inject.Inject

/**
 * The defensive net for the sign-out wipe (ADR-0021): runs on every authenticated launch and
 * wipes stale local data when a *different* user is now signed in than the one whose data the
 * device holds. Covers the paths that skip a clean sign-out — a crash mid-logout, a reinstall
 * that restores a session, or any future account switch that bypasses [LocalDataWiper].
 *
 * A first-ever login (no recorded user) never wipes: there is nothing to leak and Room is
 * already empty. Either way the current user is recorded so the next launch can compare.
 */
class AccountSwitchGuard @Inject constructor(
    private val lastUser: LastActiveUserStore,
    private val wiper: LocalDataWiper,
) {
    /**
     * @return true iff a wipe ran because the signed-in user changed.
     */
    suspend fun onAuthenticated(userId: String): Boolean {
        val previous = lastUser.lastUserId()
        val switched = previous != null && previous != userId
        if (switched) wiper.wipe()
        lastUser.setLastUserId(userId)
        return switched
    }
}
