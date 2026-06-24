package com.iponlove.app.feature.recurring.presentation

import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.RecurringError
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/** Screen state for the Recurring Rules screen (reached from the Records top bar). */
data class RecurringUiState(
    val isLoading: Boolean = true,
    val items: List<RecurringRuleListItem> = emptyList(),
    /** Picker sources for the editor. */
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val editor: RecurringEditorState? = null,
    /** A rule needs at least one account and one category to be creatable. */
    val canAdd: Boolean = false,
)

/** A rule rendered for the list, with ids resolved to names and the schedule humanized. */
data class RecurringRuleListItem(
    val id: String,
    val title: String,
    val scheduleLabel: String,
    val nextLabel: String,
    val amount: BigDecimal,
    val type: TransactionType,
)

/**
 * Editor form state. [id] null means a new rule. The transaction type isn't chosen here —
 * it's derived from the selected category at materialization.
 */
data class RecurringEditorState(
    val id: String? = null,
    val amountText: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val intervalText: String = "1",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val note: String = "",
    val errors: Set<RecurringError> = emptySet(),
) {
    val isEditing: Boolean get() = id != null
}
