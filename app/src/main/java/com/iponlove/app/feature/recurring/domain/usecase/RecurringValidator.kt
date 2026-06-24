package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.recurring.domain.model.RecurringRule

/** A single thing wrong with a recurring rule the user is editing. */
enum class RecurringError {
    AMOUNT_NOT_POSITIVE,
    ACCOUNT_REQUIRED,
    CATEGORY_REQUIRED,
    INTERVAL_INVALID,
    END_BEFORE_START,
}

/** Pure rule validation, shared by the editor (inline errors) and the upsert use case. */
object RecurringValidator {

    fun validate(rule: RecurringRule): Set<RecurringError> {
        val errors = mutableSetOf<RecurringError>()
        if (rule.template.amount.signum() <= 0) errors += RecurringError.AMOUNT_NOT_POSITIVE
        if (rule.template.accountId.isBlank()) errors += RecurringError.ACCOUNT_REQUIRED
        if (rule.template.categoryId.isBlank()) errors += RecurringError.CATEGORY_REQUIRED
        if (rule.interval < 1) errors += RecurringError.INTERVAL_INVALID
        if (rule.endDate != null && rule.endDate.isBefore(rule.nextDate)) {
            errors += RecurringError.END_BEFORE_START
        }
        return errors
    }
}
