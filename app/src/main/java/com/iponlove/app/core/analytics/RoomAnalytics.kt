package com.iponlove.app.core.analytics

import com.iponlove.app.core.analytics.local.AnalyticsEventDao
import com.iponlove.app.core.analytics.local.AnalyticsEventEntity
import com.iponlove.app.core.analytics.remote.AnalyticsRemoteSource
import com.iponlove.app.core.di.LiveSyncScope
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.userIdOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-buffered, push-only analytics (G10). Implements both seams: [Analytics.log] (fire-and-forget
 * buffer write, from feature code) and [AnalyticsFlusher.flush] (drain to Supabase, from the sync
 * layer). Dormant-safe — logging with no session or before enforcement is a harmless no-op / empty
 * flush.
 */
@Singleton
class RoomAnalytics(
    private val dao: AnalyticsEventDao,
    private val remote: AnalyticsRemoteSource,
    private val currentUser: CurrentUserProvider,
    private val scope: CoroutineScope,
    private val clock: () -> Instant,
) : Analytics, AnalyticsFlusher {

    // Hilt can't honor a Kotlin default on an @Inject param, so the real graph injects the four
    // deps and this constructor supplies the wall clock; tests use the primary to pin time.
    @Inject constructor(
        dao: AnalyticsEventDao,
        remote: AnalyticsRemoteSource,
        currentUser: CurrentUserProvider,
        @LiveSyncScope scope: CoroutineScope,
    ) : this(dao, remote, currentUser, scope, Instant::now)

    override fun log(name: String, source: String?, params: Map<String, String>) {
        // No session (e.g. logged out mid-transition) → drop; a telemetry row can't attribute.
        val userId = currentUser.userIdOrNull() ?: return
        scope.launch {
            runCatching {
                dao.insert(
                    AnalyticsEventEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        name = name,
                        source = source,
                        paramsJson = params.takeIf { it.isNotEmpty() }?.let { Json.encodeToString(it) },
                        createdAt = clock(),
                    ),
                )
                dao.trimToNewest(MAX_BUFFERED)
            }
        }
    }

    override suspend fun flush() {
        val batch = dao.all()
        if (batch.isEmpty()) return
        // If push throws (offline), the delete below never runs → rows are kept for the next flush.
        remote.push(batch.map { it.toDto() })
        dao.deleteByIds(batch.map { it.id })
    }

    private companion object {
        /** Buffer ceiling so a never-syncing device can't grow the table unbounded. */
        const val MAX_BUFFERED = 500
    }
}
