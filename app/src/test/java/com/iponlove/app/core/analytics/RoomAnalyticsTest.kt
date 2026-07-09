package com.iponlove.app.core.analytics

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.analytics.local.AnalyticsEventDao
import com.iponlove.app.core.analytics.local.AnalyticsEventEntity
import com.iponlove.app.core.analytics.remote.AnalyticsEventDto
import com.iponlove.app.core.analytics.remote.AnalyticsRemoteSource
import com.iponlove.app.core.session.CurrentUserProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.Instant

/**
 * Tier-1 for the push-only analytics primitive (S6): the [AnalyticsEventEntity.toDto] mapping
 * (incl. the params-JSON round-trip) and [RoomAnalytics]' log/flush semantics — buffer, drain,
 * offline-keep, trim, no-session drop. All JVM, no Room/Play SDK.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomAnalyticsTest {

    private val t0 = Instant.ofEpochMilli(1_000)

    /** In-memory stand-in for the Room DAO. */
    private class FakeDao : AnalyticsEventDao {
        val rows = mutableListOf<AnalyticsEventEntity>()
        override suspend fun insert(event: AnalyticsEventEntity) {
            rows.removeAll { it.id == event.id }
            rows += event
        }
        override suspend fun all(): List<AnalyticsEventEntity> = rows.sortedBy { it.createdAt }
        override suspend fun deleteByIds(ids: List<String>) { rows.removeAll { it.id in ids } }
        override suspend fun trimToNewest(keep: Int) {
            val survivors = rows.sortedByDescending { it.createdAt }.take(keep).map { it.id }.toSet()
            rows.removeAll { it.id !in survivors }
        }
    }

    private class FakeRemote(var fail: Boolean = false) : AnalyticsRemoteSource {
        val pushed = mutableListOf<AnalyticsEventDto>()
        override suspend fun push(rows: List<AnalyticsEventDto>) {
            if (fail) throw RuntimeException("offline")
            pushed += rows
        }
    }

    private fun entity(id: String, at: Instant = t0, paramsJson: String? = null) =
        AnalyticsEventEntity(id, "user-1", "paywall_impression", "settings", paramsJson, at)

    private fun analytics(
        dao: AnalyticsEventDao,
        remote: AnalyticsRemoteSource,
        scope: CoroutineScope,
        user: String? = "user-1",
    ) = RoomAnalytics(
        dao = dao,
        remote = remote,
        currentUser = CurrentUserProvider { user ?: error("no session") },
        scope = scope,
        clock = { t0 },
    )

    // ---- mapper ------------------------------------------------------------

    @Test
    fun toDto_nullParams_mapsToNull() {
        val dto = entity("a").toDto()
        assertThat(dto.params).isNull()
        assertThat(dto.userId).isEqualTo("user-1")
        assertThat(dto.name).isEqualTo("paywall_impression")
        assertThat(dto.source).isEqualTo("settings")
    }

    @Test
    fun toDto_paramsJson_parsesToObject() {
        val dto = entity("a", paramsJson = """{"feature":"calculator"}""").toDto()
        assertThat(dto.params).isNotNull()
        assertThat(dto.params!!["feature"]!!.jsonPrimitive.content).isEqualTo("calculator")
    }

    // ---- flush -------------------------------------------------------------

    @Test
    fun flush_emptyBuffer_noPush() = runTest {
        val remote = FakeRemote()
        analytics(FakeDao(), remote, this).flush()
        assertThat(remote.pushed).isEmpty()
    }

    @Test
    fun flush_pushesAll_thenDeletesThem() = runTest {
        val dao = FakeDao().apply {
            rows += entity("a", t0)
            rows += entity("b", t0.plusMillis(1))
        }
        val remote = FakeRemote()

        analytics(dao, remote, this).flush()

        assertThat(remote.pushed.map { it.id }).containsExactly("a", "b")
        assertThat(dao.rows).isEmpty()
    }

    @Test
    fun flush_pushFails_keepsBuffer() = runTest {
        val dao = FakeDao().apply { rows += entity("a") }
        val remote = FakeRemote(fail = true)

        runCatching { analytics(dao, remote, this).flush() }

        assertThat(dao.rows.map { it.id }).containsExactly("a")
    }

    // ---- log (fire-and-forget) --------------------------------------------

    @Test
    fun log_withSession_buffersRow() = runTest {
        val dao = FakeDao()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        analytics(dao, FakeRemote(), scope).log("upsell_tap", source = "calculator")

        assertThat(dao.rows).hasSize(1)
        with(dao.rows.single()) {
            assertThat(name).isEqualTo("upsell_tap")
            assertThat(source).isEqualTo("calculator")
            assertThat(userId).isEqualTo("user-1")
        }
    }

    @Test
    fun log_withParams_serializesJsonObject() = runTest {
        val dao = FakeDao()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        analytics(dao, FakeRemote(), scope).log("upsell_tap", params = mapOf("feature" to "themes"))

        assertThat(dao.rows.single().paramsJson).isEqualTo("""{"feature":"themes"}""")
    }

    @Test
    fun log_noSession_dropped() = runTest {
        val dao = FakeDao()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        analytics(dao, FakeRemote(), scope, user = null).log("paywall_impression")

        assertThat(dao.rows).isEmpty()
    }
}
