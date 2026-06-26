package com.iponlove.app.navigation

import kotlinx.coroutines.flow.Flow

/**
 * Persists the pinned bottom-bar layout. Device-local only — never synced (nav layout is a
 * per-device UI preference, exactly like the theme; ADR-0017 / ADR-0014).
 */
interface NavConfigRepository {
    fun observe(): Flow<NavConfig>
    suspend fun save(config: NavConfig)
}
