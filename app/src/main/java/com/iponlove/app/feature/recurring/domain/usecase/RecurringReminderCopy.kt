package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.TransactionType

/**
 * The user-facing wording for a due-date reminder — draft copy locked by Alvin at the grill
 * (ADR-0052). Kept as a pure object beside the detection logic, mirroring `BudgetAlertCopy`,
 * since the same strings go to both the inbox row and the OS push.
 */
object RecurringReminderCopy {

    /** [TransactionType.TRANSFER] never occurs here — categories are income/expense only. */
    fun title(categoryName: String, type: TransactionType): String = when (type) {
        TransactionType.INCOME -> "Did your $categoryName arrive?"
        TransactionType.EXPENSE -> "Have you paid for your $categoryName?"
        TransactionType.TRANSFER -> categoryName
    }

    const val BODY = "Confirm it now to record!"
}
