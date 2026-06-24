package com.iponlove.app.feature.couple.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.categories.domain.usecase.ObserveAllCategoriesUseCase
import com.iponlove.app.feature.couple.domain.usecase.CombinedLedgerCalculator
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveCombinedTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Drives the combined couple view (ADR-0011). Like Analysis, it holds no state of its own:
 * the merged stream and per-member monthly spend are derived on the fly from the live
 * transaction + category + member streams via [CombinedLedgerCalculator]. The current-month
 * window is computed from the system clock here so the calculator stays pure/testable.
 */
@HiltViewModel
class CombinedViewModel @Inject constructor(
    observeCombinedTransactions: ObserveCombinedTransactionsUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    observeCoupleMembers: ObserveCoupleMembersUseCase,
) : ViewModel() {

    val uiState: StateFlow<CombinedUiState> =
        combine(
            observeCombinedTransactions(),
            observeAllCategories(),
            observeCoupleMembers(),
        ) { transactions, categories, members ->
            if (members == null) {
                return@combine CombinedUiState(isLoading = false, isPaired = false)
            }

            val zone = ZoneId.systemDefault()
            val firstOfMonth = LocalDate.now(zone).withDayOfMonth(1)
            val monthStart = firstOfMonth.atStartOfDay(zone).toInstant()
            val monthEnd = firstOfMonth.plusMonths(1).atStartOfDay(zone).toInstant()

            val ledger = CombinedLedgerCalculator.analyze(
                transactions = transactions,
                categoryNames = categories.associateBy({ it.id }, { it.name }),
                me = members.me,
                partner = members.partner,
                monthStartInclusive = monthStart,
                monthEndExclusive = monthEnd,
            )

            CombinedUiState(
                isLoading = false,
                isPaired = true,
                monthLabel = firstOfMonth.format(MONTH_FORMAT),
                members = ledger.members,
                entries = ledger.entries,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = CombinedUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
