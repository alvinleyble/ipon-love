package com.iponlove.app.core.config.remote

/** Plain remote read of the single `app_config` row — not a synced entity (D3 / ADR-0044). */
interface AppConfigRemoteSource {
    suspend fun fetch(): AppConfigDto
}
