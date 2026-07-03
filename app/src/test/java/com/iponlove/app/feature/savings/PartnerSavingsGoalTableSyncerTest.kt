package com.iponlove.app.feature.savings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.feature.savings.data.remote.GoalContributionDto
import com.iponlove.app.feature.savings.data.remote.PartnerGoalContributionDto
import com.iponlove.app.feature.savings.data.remote.PartnerSavingsGoalDto
import com.iponlove.app.feature.savings.data.remote.SavingsGoalDto
import com.iponlove.app.feature.savings.data.remote.SavingsGoalRemoteSource
import com.iponlove.app.feature.savings.data.sync.PartnerSavingsGoalTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakePartnerGoalRemote(
    private val partnerRows: List<PartnerSavingsGoalDto>,
) : SavingsGoalRemoteSource {
    override suspend fun push(rows: List<SavingsGoalDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<SavingsGoalDto> = emptyList()
    override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerSavingsGoalDto> =
        partnerRows.filter { (it.serverRev ?: 0L) > cursor }.sortedBy { it.serverRev }.take(limit)
}

class PartnerSavingsGoalTableSyncerTest {

    private val goalDao = FakeSavingsGoalDao()
    private val contributionDao = FakeGoalContributionDao()
    private val currentUser = CurrentUserProvider { "user-1" }

    private fun syncer(rows: List<PartnerSavingsGoalDto>) = PartnerSavingsGoalTableSyncer(
        dao = goalDao,
        contributionDao = contributionDao,
        remote = FakePartnerGoalRemote(rows),
        currentUser = currentUser,
        cursors = InMemoryCursorStore(),
    )

    @Test
    fun pull_visibleSharedGoal_isUpserted() = runTest {
        syncer(listOf(partnerSavingsGoalDto(id = "pg", isShared = true, serverRev = 5))).pull()
        assertThat(goalDao.store).containsKey("pg")
    }

    @Test
    fun pull_unsharedGoal_purgesGoal_andCascadesPartnerContributionsOnly() = runTest {
        // Local replicas before the un-share crosses.
        goalDao.store["pg"] = savingsGoalEntity(id = "pg", userId = "partner-1", isShared = true)
        contributionDao.store["pc"] =
            goalContributionEntity(id = "pc", goalId = "pg", userId = "partner-1") // partner's replica
        contributionDao.store["mc"] =
            goalContributionEntity(id = "mc", goalId = "pg", userId = "user-1")    // my own funding

        // The goal crosses redacted (is_shared=false) → purge signal.
        syncer(listOf(partnerSavingsGoalDto(id = "pg", isShared = false, serverRev = 9))).pull()

        assertThat(goalDao.store).doesNotContainKey("pg")          // goal replica purged
        assertThat(contributionDao.store).doesNotContainKey("pc")  // partner contribution cascaded
        assertThat(contributionDao.store).containsKey("mc")        // my own funding left intact
    }

    @Test
    fun pull_deletedGoal_purges() = runTest {
        goalDao.store["pg"] = savingsGoalEntity(id = "pg", userId = "partner-1", isShared = true)
        syncer(listOf(partnerSavingsGoalDto(id = "pg", isShared = true, isDeleted = true, serverRev = 9))).pull()
        assertThat(goalDao.store).doesNotContainKey("pg")
    }
}
