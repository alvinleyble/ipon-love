package com.iponlove.app.feature.savings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.savings.data.GoalContributionRepositoryImpl
import com.iponlove.app.feature.savings.data.SavingsGoalRepositoryImpl
import com.iponlove.app.feature.savings.domain.usecase.DeleteSavingsGoalUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class DeleteSavingsGoalUseCaseTest {

    private val goalDao = FakeSavingsGoalDao()
    private val contributionDao = FakeGoalContributionDao()
    private val clock = SyncClock(now = { Instant.ofEpochMilli(20_000) })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val useCase = DeleteSavingsGoalUseCase(
        goals = SavingsGoalRepositoryImpl(goalDao, clock, currentUser),
        contributions = GoalContributionRepositoryImpl(contributionDao, clock, currentUser),
    )

    @Test
    fun delete_softDeletesGoal_andCascadesOwnContributionsOnly() = runTest {
        goalDao.store["g"] = savingsGoalEntity(id = "g", userId = "user-1", isShared = true)
        contributionDao.store["mine1"] = goalContributionEntity(id = "mine1", goalId = "g", userId = "user-1")
        contributionDao.store["mine2"] = goalContributionEntity(id = "mine2", goalId = "g", userId = "user-1")
        contributionDao.store["partner"] = goalContributionEntity(id = "partner", goalId = "g", userId = "partner-1")

        useCase("g")

        assertThat(goalDao.store.getValue("g").isDeleted).isTrue()
        assertThat(contributionDao.store.getValue("mine1").isDeleted).isTrue()
        assertThat(contributionDao.store.getValue("mine2").isDeleted).isTrue()
        // The partner's contributions aren't ours to delete (RLS); they fall out via the
        // goal-deleted redaction on the partner's device.
        assertThat(contributionDao.store.getValue("partner").isDeleted).isFalse()
    }
}
