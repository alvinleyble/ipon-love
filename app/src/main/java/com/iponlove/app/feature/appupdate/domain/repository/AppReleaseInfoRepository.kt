package com.iponlove.app.feature.appupdate.domain.repository

/** Plain remote read, not a synced entity (ADR-0029) — no Room, no TableSyncer. */
interface AppReleaseInfoRepository {
    suspend fun getRequiredVersionCode(): Int
}
