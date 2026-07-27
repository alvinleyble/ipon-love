package com.iponlove.app.feature.notifications

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.notifications.data.sync.NotificationTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class NotificationTableSyncerTest {

    private val dao = FakeNotificationDao()
    private val remote = FakeNotificationRemote()
    private val cursors = InMemoryCursorStore()
    private val syncer = NotificationTableSyncer(dao, remote, cursors, ConflictResolver())

    @Test
    fun usesNotificationsTable() {
        assertThat(syncer.table).isEqualTo(SyncTable.NOTIFICATIONS)
    }

    /**
     * The inbox is a leaf that nothing depends on, and it must never delay a financial row's
     * push — so it sorts last in the FK order (ADR-0009 / ADR-0053).
     */
    @Test
    fun sortsLastInTheFkOrder() {
        assertThat(SyncTable.NOTIFICATIONS.ordinal)
            .isEqualTo(SyncTable.entries.maxOf { it.ordinal })
    }

    @Test
    fun push_sendsDirtyRowsOnly_andClearsAckedFlag() = runTest {
        dao.store["a"] = notificationEntity("a", pendingSync = true)
        dao.store["b"] = notificationEntity("b", pendingSync = false)

        syncer.push()

        assertThat(remote.pushed.map { it.id }).containsExactly("a")
        assertThat(dao.store.getValue("a").pendingSync).isFalse()
    }

    @Test
    fun pull_mapsRemoteRowsToEntities_andAdvancesCursor() = runTest {
        remote.serverRows += notificationDto("a", isRead = true, serverRev = 12)

        syncer.pull()

        val row = dao.store.getValue("a")
        assertThat(row.isRead).isTrue()
        assertThat(row.pendingSync).isFalse()
        assertThat(cursors.cursor(SyncTable.NOTIFICATIONS)).isEqualTo(12)
    }

    /**
     * The one field that genuinely races across a user's own devices is `isRead`; plain
     * row-level LWW is the intended resolution — the later mark-as-read simply wins.
     */
    @Test
    fun pull_keepsTheNewerLocalReadState() = runTest {
        dao.store["a"] = notificationEntity(
            "a",
            isRead = true,
            updatedAt = Instant.ofEpochMilli(9_000),
            pendingSync = true,
        )
        remote.serverRows += notificationDto(
            "a",
            isRead = false,
            updatedAt = Instant.ofEpochMilli(5_000),
            serverRev = 3,
        )

        syncer.pull()

        assertThat(dao.store.getValue("a").isRead).isTrue()
    }
}
