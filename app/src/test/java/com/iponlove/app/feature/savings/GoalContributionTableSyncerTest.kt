package com.iponlove.app.feature.savings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.feature.savings.data.remote.GoalContributionDto
import com.iponlove.app.feature.savings.data.remote.GoalContributionRemoteSource
import com.iponlove.app.feature.savings.data.remote.PartnerGoalContributionDto
import com.iponlove.app.feature.savings.data.sync.GoalContributionTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The F1 guard: a dirty contribution whose goal is no longer locally pushable (unshared,
 * deleted, or otherwise absent) is skipped by [GoalContributionTableSyncer.push] so it can never
 * be RLS-rejected in a loop and wedge the whole sync. Contributions to a still-pushable goal
 * push normally (ADR-0025).
 */
class GoalContributionTableSyncerTest {

    private val contributionDao = FakeGoalContributionDao()
    private val goalDao = FakeSavingsGoalDao()
    private val currentUser = CurrentUserProvider { "user-1" }
    private val remote = RecordingRemote()

    private val syncer = GoalContributionTableSyncer(
        dao = contributionDao,
        goalDao = goalDao,
        currentUser = currentUser,
        remote = remote,
        cursors = InMemoryCursorStore(),
        resolver = ConflictResolver(),
    )

    /** Records exactly which contribution ids were sent to the server. */
    private class RecordingRemote : GoalContributionRemoteSource {
        val pushed = mutableListOf<String>()
        override suspend fun push(rows: List<GoalContributionDto>): List<String> {
            pushed += rows.map { it.id }
            return rows.map { it.id }
        }
        override suspend fun pull(cursor: Long, limit: Int) = emptyList<GoalContributionDto>()
        override suspend fun pullPartner(cursor: Long, limit: Int) =
            emptyList<PartnerGoalContributionDto>()
    }

    @Test
    fun push_skipsContributionWhoseGoalIsNoLongerPushable() = runTest {
        // Goal G was unshared/deleted out from under a still-pending offline contribution: gone
        // from the local pushable set. Its contribution must NOT be sent (would RLS-reject → wedge).
        contributionDao.store["orphan"] =
            goalContributionEntity(id = "orphan", goalId = "G", userId = "user-1", pendingSync = true)

        val sent = syncer.push()

        assertThat(sent).isFalse()
        assertThat(remote.pushed).isEmpty()
        // Row stays dirty, but as a benign local orphan — not retried against the server.
        assertThat(contributionDao.store.getValue("orphan").pendingSync).isTrue()
    }

    @Test
    fun push_sendsContributionToOwnGoal() = runTest {
        goalDao.store["G"] = savingsGoalEntity(id = "G", userId = "user-1")
        contributionDao.store["c"] =
            goalContributionEntity(id = "c", goalId = "G", userId = "user-1", pendingSync = true)

        syncer.push()

        assertThat(remote.pushed).containsExactly("c")
        assertThat(contributionDao.store.getValue("c").pendingSync).isFalse()
    }

    @Test
    fun push_sendsOwnContributionToAPartnerSharedGoal() = runTest {
        // A goal shared into the couple (owned by the partner) is still pushable — both partners fund it.
        goalDao.store["G"] =
            savingsGoalEntity(id = "G", userId = "partner-1", isShared = true, coupleId = "c-1")
        contributionDao.store["c"] =
            goalContributionEntity(id = "c", goalId = "G", userId = "user-1", pendingSync = true)

        syncer.push()

        assertThat(remote.pushed).containsExactly("c")
    }

    @Test
    fun push_sendsPushableAndSkipsOrphan_inTheSameBatch() = runTest {
        goalDao.store["live"] = savingsGoalEntity(id = "live", userId = "user-1")
        contributionDao.store["good"] =
            goalContributionEntity(id = "good", goalId = "live", userId = "user-1", pendingSync = true)
        contributionDao.store["orphan"] =
            goalContributionEntity(id = "orphan", goalId = "gone", userId = "user-1", pendingSync = true)

        syncer.push()

        // The good row still flushes; only the orphan is held back — one poison row can't block it.
        assertThat(remote.pushed).containsExactly("good")
        assertThat(contributionDao.store.getValue("good").pendingSync).isFalse()
        assertThat(contributionDao.store.getValue("orphan").pendingSync).isTrue()
    }
}
