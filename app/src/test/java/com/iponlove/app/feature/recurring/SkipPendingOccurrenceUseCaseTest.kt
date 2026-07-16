package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.recurring.data.RecurringRuleRepositoryImpl
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.SkipPendingOccurrenceUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SkipPendingOccurrenceUseCaseTest {

    private val ruleDao = FakeRecurringRuleDao()
    private val clock = SyncClock(now = { Instant.ofEpochMilli(1_000) })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val ruleRepository = RecurringRuleRepositoryImpl(ruleDao, clock, currentUser)
    private val skip = SkipPendingOccurrenceUseCase(ruleRepository)

    @Test
    fun advancesCursor_pastTheSkippedDate() = runTest {
        ruleDao.store["r"] = ruleEntity("r", frequency = RecurringFrequency.MONTHLY, nextDate = jun(1))

        skip("r", jun(1))

        // Cursor jumps to the occurrence after Jun 1, so Jun 1 leaves the pending window.
        assertThat(ruleRepository.getRule("r")!!.nextDate).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun isDateAware_notCursorAware_soItWorksWhenCursorLagsBehindTheFloor() = runTest {
        // Cursor parked far back (Jan), but the user skips the Jun occurrence shown on the card.
        ruleDao.store["r"] =
            ruleEntity("r", frequency = RecurringFrequency.MONTHLY, nextDate = LocalDate.of(2026, 1, 1))

        skip("r", jun(1))

        assertThat(ruleRepository.getRule("r")!!.nextDate).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun doesNotRegressCursor_whenSkippingAnOlderDate() = runTest {
        ruleDao.store["r"] =
            ruleEntity("r", frequency = RecurringFrequency.MONTHLY, nextDate = LocalDate.of(2026, 7, 1))

        // advance(May 1) = Jun 1, which is NOT after the current cursor (Jul 1) → no change.
        skip("r", LocalDate.of(2026, 5, 1))

        assertThat(ruleRepository.getRule("r")!!.nextDate).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun respectsInterval() = runTest {
        // Fortnightly rule (weekly, interval 2): skipping steps a full 2 weeks.
        ruleDao.store["r"] =
            ruleEntity("r", frequency = RecurringFrequency.WEEKLY, interval = 2, nextDate = jun(1))

        skip("r", jun(1))

        assertThat(ruleRepository.getRule("r")!!.nextDate).isEqualTo(LocalDate.of(2026, 6, 15))
    }

    @Test
    fun unknownRule_isNoOp() = runTest {
        skip("ghost", jun(1))
        assertThat(ruleDao.store).isEmpty()
    }

    private fun jun(day: Int) = LocalDate.of(2026, 6, day)
}
