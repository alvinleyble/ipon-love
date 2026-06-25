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
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
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
    private val alertStore: BudgetAlertStore,
    private val notifier: BudgetAlertNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val currentMonth = yearMonthKey(Instant.now())

        val budgets = budgetRepository.observeBudgets().first()
        val transactions = transactionRepository.observeTransactions().first()

        val pairingState = observePairingState().first()
        val sharedBudgets = if (pairingState is PairingState.Paired) {
            budgetRepository.observeSharedBudgets(pairingState.couple.id).first()
        } else {
            emptyList()
        }

        val alreadyFired = alertStore.loadFired(currentMonth)
        val alerts = checkBudgetAlerts(
            budgets = budgets + sharedBudgets,
            transactions = transactions,
            alreadyFiredKeys = alreadyFired,
            currentMonth = currentMonth,
        )

        for (alert in alerts) {
            val categoryName = alert.budget.categoryId?.let { categoryRepository.getCategory(it)?.name }
            notifier.fire(alert, categoryName)
            alertStore.markFired(alert.dedupeKey, currentMonth)
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "ipon_budget_alerts"

        fun buildRequest() = OneTimeWorkRequestBuilder<BudgetAlertWorker>().build()
    }
}
