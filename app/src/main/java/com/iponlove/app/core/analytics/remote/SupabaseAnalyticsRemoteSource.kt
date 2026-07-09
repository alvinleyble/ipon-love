package com.iponlove.app.core.analytics.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject

/** Postgrest-backed [AnalyticsRemoteSource]. Upsert by `id` (idempotent), RLS-scoped to own rows. */
class SupabaseAnalyticsRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : AnalyticsRemoteSource {

    override suspend fun push(rows: List<AnalyticsEventDto>) {
        if (rows.isEmpty()) return
        client.from("analytics_events").upsert(rows)
    }
}
