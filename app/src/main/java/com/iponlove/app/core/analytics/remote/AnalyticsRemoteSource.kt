package com.iponlove.app.core.analytics.remote

/** Push-only remote sink for buffered telemetry (G10). No pull — the client never reads events back. */
interface AnalyticsRemoteSource {
    /** Upsert a batch (idempotent by [AnalyticsEventDto.id] so a retried flush can't duplicate). */
    suspend fun push(rows: List<AnalyticsEventDto>)
}
