package com.iponlove.app.core.session

import javax.inject.Inject

/**
 * Supplies the signed-in user's id, stamped onto every owned row (accounts,
 * transactions, …) so sync and RLS can attribute it.
 *
 * Until the auth slice lands, [DevCurrentUserProvider] returns a fixed dev id so
 * features can be built and run offline. ADR-0013 makes the user's own row a normal
 * synced entity, so swapping in the real session-backed provider later is a drop-in.
 */
fun interface CurrentUserProvider {
    fun userId(): String
}

/** Placeholder provider for offline development — replaced when auth is wired. */
class DevCurrentUserProvider @Inject constructor() : CurrentUserProvider {
    override fun userId(): String = DEV_USER_ID

    private companion object {
        const val DEV_USER_ID = "00000000-0000-0000-0000-000000000001"
    }
}
