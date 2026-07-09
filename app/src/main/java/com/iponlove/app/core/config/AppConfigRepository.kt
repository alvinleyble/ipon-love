package com.iponlove.app.core.config

import kotlinx.coroutines.flow.Flow

/**
 * The premium paywall remote-override config, offline-first (Room-cached, D3 / ADR-0044).
 * [observe] is always local + instant; [refresh] re-fetches the remote row and updates the
 * cache (wired into the foreground sync trigger by the S4 reconcile loop).
 */
interface AppConfigRepository {

    /** The cached config, or [AppConfig.DORMANT] until the first successful [refresh]. */
    fun observe(): Flow<AppConfig>

    /** Fetch the remote `app_config` row and cache it. No-op on network failure. */
    suspend fun refresh()
}
