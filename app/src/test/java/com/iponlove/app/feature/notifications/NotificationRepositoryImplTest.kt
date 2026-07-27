package com.iponlove.app.feature.notifications

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.notifications.data.NotificationRepositoryImpl
import com.iponlove.app.feature.notifications.domain.model.NotificationCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NotificationRepositoryImplTest {

    private val dao = FakeNotificationDao()
    private val remote = FakeNotificationRemote()
    private var now = Instant.parse("2026-07-26T10:00:00Z")
    private val clock = SyncClock(now = { now })
    private val currentUser = CurrentUserProvider { "user-1" }

    private fun repo() = NotificationRepositoryImpl(
        dao = dao,
        remote = remote,
        clock = clock,
        currentUser = currentUser,
    )

    // ---- create-if-absent (ADR-0053 decision 3) ----

    @Test
    fun record_createsANewRow_andReportsIt() = runTest {
        val created = repo().record("budget:b1:2026-07:warn", NotificationCategory.BUDGET, "t", "b")

        assertThat(created).isTrue()
        val row = dao.store.getValue("budget:b1:2026-07:warn")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.category).isEqualTo("budget")
        assertThat(row.isRead).isFalse()
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun record_isANoOpForAnAlreadyRaisedId() = runTest {
        val repo = repo()
        repo.record("budget:b1:2026-07:warn", NotificationCategory.BUDGET, "first", "b")

        val second = repo.record("budget:b1:2026-07:warn", NotificationCategory.BUDGET, "second", "b")

        assertThat(second).isFalse()
        assertThat(dao.store.getValue("budget:b1:2026-07:warn").title).isEqualTo("first")
    }

    /** The whole point of create-if-absent: a dismissed alert must never come back. */
    @Test
    fun record_neverResurrectsADismissedRow() = runTest {
        val repo = repo()
        repo.record("budget:b1:2026-07:warn", NotificationCategory.BUDGET, "t", "b")
        repo.dismiss("budget:b1:2026-07:warn")

        val created = repo.record("budget:b1:2026-07:warn", NotificationCategory.BUDGET, "t", "b")

        assertThat(created).isFalse()
        assertThat(dao.store.getValue("budget:b1:2026-07:warn").isDeleted).isTrue()
    }

    /** Re-detecting an event must not silently mark a read notification unread again. */
    @Test
    fun record_doesNotClobberReadState() = runTest {
        val repo = repo()
        repo.record("budget:b1:2026-07:warn", NotificationCategory.BUDGET, "t", "b")
        repo.markAllRead()

        repo.record("budget:b1:2026-07:warn", NotificationCategory.BUDGET, "t", "b")

        assertThat(dao.store.getValue("budget:b1:2026-07:warn").isRead).isTrue()
    }

    @Test
    fun raisedIds_includesDismissedRows_soTheyNeverRefire() = runTest {
        val repo = repo()
        repo.record("budget:b1:2026-07:warn", NotificationCategory.BUDGET, "t", "b")
        repo.record("budget:b2:2026-07:limit", NotificationCategory.BUDGET, "t", "b")
        repo.record("recurring:occ-1", NotificationCategory.RECURRING, "t", "b")
        repo.dismiss("budget:b1:2026-07:warn")

        assertThat(repo.raisedIds("budget:"))
            .containsExactly("budget:b1:2026-07:warn", "budget:b2:2026-07:limit")
    }

    // ---- read model ----

    @Test
    fun unreadCount_countsOnlyVisibleUnreadRows() = runTest {
        val repo = repo()
        // Sampled after each mutation rather than streamed: the point is which rows *count*
        // (dismissed and read ones don't), not how many intermediate emissions the flow makes.
        suspend fun unread() = repo.observeUnreadCount().first()

        assertThat(unread()).isEqualTo(0)

        repo.record("a", NotificationCategory.BUDGET, "t", "b")
        repo.record("b", NotificationCategory.BUDGET, "t", "b")
        assertThat(unread()).isEqualTo(2)

        repo.dismiss("a")
        assertThat(unread()).isEqualTo(1)

        repo.markAllRead()
        assertThat(unread()).isEqualTo(0)
    }

    @Test
    fun markAllRead_marksRowsDirtySoTheReadStateSyncs() = runTest {
        val repo = repo()
        repo.record("a", NotificationCategory.BUDGET, "t", "b")
        dao.clearPending(listOf("a"))

        repo.markAllRead()

        val row = dao.store.getValue("a")
        assertThat(row.isRead).isTrue()
        assertThat(row.pendingSync).isTrue()
        assertThat(row.updatedAt).isGreaterThan(row.createdAt)
    }

    @Test
    fun clearAll_softDeletesEveryVisibleRow() = runTest {
        val repo = repo()
        repo.record("a", NotificationCategory.BUDGET, "t", "b")
        repo.record("b", NotificationCategory.RECURRING, "t", "b")

        repo.clearAll()

        assertThat(dao.store.values.map { it.isDeleted }).containsExactly(true, true)
        assertThat(dao.store.values.map { it.pendingSync }).containsExactly(true, true)
    }

    @Test
    fun inbox_hidesDismissedRows_newestFirst() = runTest {
        dao.store["old"] = notificationEntity("old", createdAt = Instant.ofEpochMilli(1_000))
        dao.store["new"] = notificationEntity("new", createdAt = Instant.ofEpochMilli(9_000))
        dao.store["gone"] = notificationEntity("gone", isDeleted = true)

        repo().observeInbox().test {
            assertThat(awaitItem().map { it.id }).containsExactly("new", "old").inOrder()
        }
    }

    // ---- 60-day retention (ADR-0053 decision 4) ----

    @Test
    fun prune_hardDeletesOnlyRowsPastTheCutoff() = runTest {
        dao.store["old"] = notificationEntity("old", createdAt = now.minus(Duration.ofDays(61)), serverRev = 5)
        dao.store["fresh"] = notificationEntity("fresh", createdAt = now.minus(Duration.ofDays(59)), serverRev = 6)

        val removed = repo().pruneExpired(Duration.ofDays(60))

        assertThat(removed).isEqualTo(1)
        assertThat(dao.store.keys).containsExactly("fresh")
        assertThat(remote.deleted).containsExactly("old")
    }

    /** Deleting locally first would orphan the row server-side with no id left to clean it up. */
    @Test
    fun prune_keepsRowsLocallyWhenTheRemoteDeleteFails() = runTest {
        remote.failDelete = true
        dao.store["old"] = notificationEntity("old", createdAt = now.minus(Duration.ofDays(61)), serverRev = 5)

        val removed = repo().pruneExpired(Duration.ofDays(60))

        assertThat(removed).isEqualTo(0)
        assertThat(dao.store.keys).containsExactly("old")
    }

    /**
     * A row this device pushed itself keeps `serverRev = null` locally (push only clears the
     * dirty flag), so the sweep must NOT use that column to decide whether the server has a copy
     * — treating a null as "never pushed" would skip the remote delete and orphan the row.
     */
    @Test
    fun prune_stillDeletesRemotelyWhenLocalServerRevIsNull() = runTest {
        dao.store["old"] = notificationEntity(
            "old",
            createdAt = now.minus(Duration.ofDays(61)),
            serverRev = null,
            pendingSync = false,
        )

        val removed = repo().pruneExpired(Duration.ofDays(60))

        assertThat(removed).isEqualTo(1)
        assertThat(remote.deleted).containsExactly("old")
        assertThat(dao.store).isEmpty()
    }

    @Test
    fun prune_doesNothingWhenNothingIsExpired() = runTest {
        dao.store["fresh"] = notificationEntity("fresh", createdAt = now, serverRev = 1)

        assertThat(repo().pruneExpired(Duration.ofDays(60))).isEqualTo(0)
        assertThat(remote.deleted).isEmpty()
    }
}
