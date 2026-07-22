package com.iponlove.app.feature.budgets.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.iponlove.app.feature.budgets.data.BudgetAlertStore
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import com.iponlove.app.feature.budgets.domain.usecase.CheckBudgetAlertsUseCase
import com.iponlove.app.feature.budgets.domain.usecase.yearMonthKey
import com.iponlove.app.feature.budgets.presentation.BudgetAlertNotifier
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.categories.domain.usecase.AnalysisExclusion
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetAlertsEnabledUseCase
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant

@HiltWorker
class BudgetAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val observePairingState: ObservePairingStateUseCase,
    private val checkBudgetAlerts: CheckBudgetAlertsUseCase,
    private val observeBudgetAlertsEnabled: ObserveBudgetAlertsEnabledUseCase,
    private val alertStore: BudgetAlertStore,
    private val notifier: BudgetAlertNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = Instant.now()
        val currentCalendarMonth = yearMonthKey(now)

        val budgets = budgetRepository.observeBudgets().first()
        val transactions = transactionRepository.observeTransactions().first()

        val pairingState = observePairingState().first()
        val sharedBudgets = if (pairingState is PairingState.Paired) {
            budgetRepository.observeSharedBudgets(pairingState.couple.id).first()
        } else {
            emptyList()
        }
        // Shared budgets count BOTH partners' non-private spending (Item 35), so their alerts must
        // be checked against the combined ledger — not the user's own transactions like personal
        // budgets. Only fetched when there are shared budgets to check.
        val combinedTransactions = if (sharedBudgets.isNotEmpty()) {
            transactionRepository.observeCombinedTransactionsUnbounded().first()
        } else {
            emptyList()
        }

        // Pass-through categories (reimbursables, ADR-0049) never consume a budget, so they must
        // not trip a budget alert either. observeAllCategories() covers both owners' flags, so the
        // shared/combined path excludes the partner's reimbursables too (parity with the Budgets tab).
        val excludedIds = AnalysisExclusion.excludedIds(categoryRepository.observeAllCategories().first())

        val alreadyFired = alertStore.loadFired(currentCalendarMonth)
        val alerts = checkBudgetAlerts(
            budgets = budgets,
            transactions = AnalysisExclusion.retainAnalyzable(transactions, excludedIds) { it.categoryId },
            alreadyFiredKeys = alreadyFired,
            currentMonth = currentCalendarMonth,
        ) + checkBudgetAlerts(
            budgets = sharedBudgets,
            transactions = AnalysisExclusion.retainAnalyzable(combinedTransactions, excludedIds) { it.categoryId },
            alreadyFiredKeys = alreadyFired,
            currentMonth = currentCalendarMonth,
        )

        // Marking fired even when suppressed (Item 7) prevents a backlog of stale alerts from
        // dumping all at once if the user re-enables the toggle after several crossings.
        val alertsEnabled = observeBudgetAlertsEnabled.invoke().first()
        for (alert in alerts) {
            if (alertsEnabled) {
                val categoryName = alert.budget.categoryId?.let { categoryRepository.getCategory(it)?.name }
                notifier.fire(alert, categoryName)
            }
            alertStore.markFired(alert.dedupeKey, currentCalendarMonth)
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "ipon_budget_alerts"

        fun buildRequest() = OneTimeWorkRequestBuilder<BudgetAlertWorker>().build()
    }
}
