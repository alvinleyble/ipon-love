package com.iponlove.app.feature.recurring.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.model.RecurringRule
import com.iponlove.app.feature.recurring.domain.model.RecurringTemplate
import com.iponlove.app.feature.recurring.domain.usecase.DeleteRecurringRuleUseCase
import com.iponlove.app.feature.recurring.domain.usecase.MaterializeRecurringRulesUseCase
import com.iponlove.app.feature.recurring.domain.usecase.ObserveRecurringRulesUseCase
import com.iponlove.app.feature.recurring.domain.usecase.RecurringValidator
import com.iponlove.app.feature.recurring.domain.usecase.UpsertRecurringRuleUseCase
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecurringViewModel @Inject constructor(
    observeRules: ObserveRecurringRulesUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val upsertRule: UpsertRecurringRuleUseCase,
    private val deleteRule: DeleteRecurringRuleUseCase,
    private val materializeRules: MaterializeRecurringRulesUseCase,
) : ViewModel() {

    private val editor = MutableStateFlow<RecurringEditorState?>(null)

    // Latest domain rules, captured so the editor can rehydrate the full rule on edit.
    private var latestRules: List<RecurringRule> = emptyList()
    private var firstAccountId: String? = null
    private var firstCategoryId: String? = null

    val uiState: StateFlow<RecurringUiState> =
        combine(
            observeRules(),
            observeAccounts(),
            observeCategories(),
            editor,
        ) { rules, accounts, categories, editorState ->
            latestRules = rules
            firstAccountId = accounts.firstOrNull()?.id
            firstCategoryId = categories.firstOrNull()?.id

            val categoryNames = categories.associate { it.id to it.name }
            val categoryTypes = categories.associate { it.id to it.type }

            RecurringUiState(
                isLoading = false,
                items = rules.map { it.toListItem(categoryNames, categoryTypes) },
                accounts = accounts,
                categories = categories,
                editor = editorState,
                canAdd = accounts.isNotEmpty() && categories.isNotEmpty(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = RecurringUiState(),
        )

    init {
        // Catch up any rules that came due while the app was closed.
        viewModelScope.launch { materializeRules() }
    }

    fun startCreate() {
        editor.value = RecurringEditorState(
            accountId = firstAccountId,
            categoryId = firstCategoryId,
        )
    }

    fun startEdit(id: String) {
        val rule = latestRules.firstOrNull { it.id == id } ?: return
        editor.value = RecurringEditorState(
            id = rule.id,
            amountText = rule.template.amount.toPlainString(),
            accountId = rule.template.accountId,
            categoryId = rule.template.categoryId,
            frequency = rule.frequency,
            intervalText = rule.interval.toString(),
            startDate = rule.nextDate,
            endDate = rule.endDate,
            note = rule.template.note.orEmpty(),
        )
    }

    fun cancelEdit() {
        editor.value = null
    }

    fun onAmountChange(value: String) = editor.update { it?.copy(amountText = value, errors = emptySet()) }

    fun onAccountChange(id: String) = editor.update { it?.copy(accountId = id, errors = emptySet()) }

    fun onCategoryChange(id: String) = editor.update { it?.copy(categoryId = id, errors = emptySet()) }

    fun onFrequencyChange(frequency: RecurringFrequency) =
        editor.update { it?.copy(frequency = frequency, errors = emptySet()) }

    fun onIntervalChange(value: String) = editor.update { it?.copy(intervalText = value, errors = emptySet()) }

    fun onStartDateChange(date: java.time.LocalDate) =
        editor.update { it?.copy(startDate = date, errors = emptySet()) }

    fun onEndDateChange(date: java.time.LocalDate?) =
        editor.update { it?.copy(endDate = date, errors = emptySet()) }

    fun onNoteChange(value: String) = editor.update { it?.copy(note = value) }

    fun save() {
        val s = editor.value ?: return
        val rule = RecurringRule(
            id = s.id ?: UUID.randomUUID().toString(),
            frequency = s.frequency,
            interval = s.intervalText.trim().toIntOrNull() ?: 0,
            nextDate = s.startDate,
            endDate = s.endDate,
            template = RecurringTemplate(
                amount = s.amountText.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO,
                accountId = s.accountId.orEmpty(),
                categoryId = s.categoryId.orEmpty(),
                note = s.note.trim().ifBlank { null },
            ),
        )

        val errors = RecurringValidator.validate(rule)
        if (errors.isNotEmpty()) {
            editor.value = s.copy(errors = errors)
            return
        }

        viewModelScope.launch {
            upsertRule(rule)
            editor.value = null
            // Generate immediately if the new rule is already due (start on/before today).
            materializeRules()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteRule(id) }
    }

    private fun RecurringRule.toListItem(
        categoryNames: Map<String, String>,
        categoryTypes: Map<String, CategoryType>,
    ): RecurringRuleListItem {
        val ended = endDate != null && nextDate.isAfter(endDate)
        return RecurringRuleListItem(
            id = id,
            title = categoryNames[template.categoryId] ?: "Category",
            scheduleLabel = scheduleLabel(),
            nextLabel = if (ended) "Ended" else "Next: ${nextDate.format(DATE_FORMATTER)}",
            amount = template.amount,
            type = categoryTypes[template.categoryId]?.toTransactionType() ?: TransactionType.EXPENSE,
        )
    }

    private fun RecurringRule.scheduleLabel(): String = when (frequency) {
        RecurringFrequency.DAILY -> if (interval == 1) "Daily" else "Every $interval days"
        RecurringFrequency.WEEKLY -> if (interval == 1) "Weekly" else "Every $interval weeks"
        RecurringFrequency.MONTHLY -> if (interval == 1) "Monthly" else "Every $interval months"
    }

    private fun CategoryType.toTransactionType(): TransactionType = when (this) {
        CategoryType.INCOME -> TransactionType.INCOME
        CategoryType.EXPENSE -> TransactionType.EXPENSE
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    }
}
