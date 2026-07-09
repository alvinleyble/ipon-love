package com.iponlove.app.core.analytics.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A buffered paywall-funnel telemetry event (G10 / §10.10). **Write-only, push-only** — never
 * pulled, never LWW-merged, no [com.iponlove.app.core.sync.SyncMeta]. Logged fire-and-forget into
 * this Room table, then flushed to Supabase on the full-sync trigger and deleted locally once it
 * lands (see [com.iponlove.app.core.analytics.AnalyticsFlusher]).
 *
 * [id] is a client-generated UUID so a retried flush is idempotent by upsert. [paramsJson] is an
 * optional serialized JSON object (extra key/values); null when the event carries none.
 */
@Entity(tableName = "analytics_events")
data class AnalyticsEventEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val source: String?,
    val paramsJson: String?,
    val createdAt: Instant,
)
