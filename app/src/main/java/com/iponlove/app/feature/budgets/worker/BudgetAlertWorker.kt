package com.iponlove.app.feature.budgets.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.iponlove.app.feature.budgets.data.BudgetAlertStore
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import com.iponlove.app.feature.budgets.domain.usecase.BudgetCycle
import com.iponlove.app.feature.budgets.domain.usecase.CheckBudgetAlertsUseCase
import com.iponlove.app.feature.budgets.domain.usecase.yearMonthKey
import com.iponlove.app.feature.budgets.presentation.BudgetAlertNotifier
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetStartDayUseCase
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
    private val observeBudgetStartDay: ObserveBudgetStartDayUseCase,
    private val alertStore: BudgetAlertStore,
    private val notifier: BudgetAlertNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = Instant.now()
        val startDay = observeBudgetStartDay().first()
        // Personal budgets follow the payday-aligned cycle (ADR-0046); the shared couple budget
        // stays on calendar months (a per-user cycle can't bind a two-user row). The dedup store's
        // clear-on-rollover is scoped to the calendar month — a coarser but harmless GC cadence
        // (stale keys never match a fresh cycle's budgetId+yearMonth).
        val currentCalendarMonth = yearMonthKey(now)
        val currentCycle = BudgetCycle.cycleKey(now, startDay)

        val budgets = budgetRepository.observeBudgets().first()
        val transactions = transactionRepository.observeTransactions().first()

        val pairingState = observePairingState().first()
        val sharedBudgets = if (pairingState is PairingState.Paired) {
            budgetRepository.observeSharedBudgets(pairingState.couple.id).first()
        } else {
            emptyList()
        }

        val alreadyFired = alertStore.loadFired(currentCalendarMonth)
        val alerts = checkBudgetAlerts(
            budgets = budgets,
            transactions = transactions,
            alreadyFiredKeys = alreadyFired,
            currentMonth = currentCycle,
            startDay = startDay,
        ) + checkBudgetAlerts(
            budgets = sharedBudgets,
            transactions = transactions,
            alreadyFiredKeys = alreadyFired,
            currentMonth = currentCalendarMonth,
            startDay = 1,
        )

        for (alert in alerts) {
            val categoryName = alert.budget.categoryId?.let { categoryRepository.getCategory(it)?.name }
            notifier.fire(alert, categoryName)
            alertStore.markFired(alert.dedupeKey, currentCalendarMonth)
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "ipon_budget_alerts"

        fun buildRequest() = OneTimeWorkRequestBuilder<BudgetAlertWorker>().build()
    }
}
