package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.recurring.domain.usecase.RecurringError
import com.iponlove.app.feature.recurring.domain.usecase.RecurringValidator
import org.junit.Test
import java.time.LocalDate

class RecurringValidatorTest {

    @Test
    fun validRule_hasNoErrors() {
        assertThat(RecurringValidator.validate(rule("r"))).isEmpty()
    }

    @Test
    fun nonPositiveAmount_isFlagged() {
        assertThat(RecurringValidator.validate(rule("r", amount = "0")))
            .contains(RecurringError.AMOUNT_NOT_POSITIVE)
    }

    @Test
    fun blankAccountOrCategory_isFlagged() {
        val errors = RecurringValidator.validate(rule("r", accountId = "", categoryId = ""))
        assertThat(errors).containsAtLeast(
            RecurringError.ACCOUNT_REQUIRED,
            RecurringError.CATEGORY_REQUIRED,
        )
    }

    @Test
    fun intervalBelowOne_isFlagged() {
        assertThat(RecurringValidator.validate(rule("r", interval = 0)))
            .contains(RecurringError.INTERVAL_INVALID)
    }

    @Test
    fun endBeforeStart_isFlagged() {
        val r = rule(
            "r",
            nextDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 5, 1),
        )
        assertThat(RecurringValidator.validate(r)).contains(RecurringError.END_BEFORE_START)
    }

    @Test
    fun endEqualsStart_isAllowed() {
        val r = rule(
            "r",
            nextDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 6, 1),
        )
        assertThat(RecurringValidator.validate(r)).doesNotContain(RecurringError.END_BEFORE_START)
    }
}
