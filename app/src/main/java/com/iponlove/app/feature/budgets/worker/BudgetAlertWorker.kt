package com.iponlove.app.feature.budgets.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.iponlove.app.MainActivity
import com.iponlove.app.feature.budgets.data.BudgetOverAlertBacklogStore
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.usecase.BudgetAlertCopy
import com.iponlove.app.feature.budgets.domain.usecase.BudgetLineId
import com.iponlove.app.feature.budgets.domain.usecase.CheckBudgetAlertsUseCase
import com.iponlove.app.feature.budgets.domain.usecase.BudgetAlertSlot
import com.iponlove.app.feature.budgets.domain.usecase.yearMonthKey
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.categories.domain.usecase.AnalysisExclusion
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.notifications.domain.model.AppNotification
import com.iponlove.app.feature.notifications.domain.model.NotificationCategory
import com.iponlove.app.feature.notifications.domain.usecase.GetRaisedNotificationIdsUseCase
import com.iponlove.app.feature.notifications.domain.usecase.PruneExpiredNotificationsUseCase
import com.iponlove.app.feature.notifications.domain.usecase.RecordNotificationUseCase
import com.iponlove.app.feature.notifications.presentation.SystemNotificationPresenter
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetAlertsEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetOverAlertsEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetOverThresholdUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetWarnThresholdUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveMutedBudgetLinesUseCase
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * The pilot producer for the notification inbox (ADR-0053): every crossed budget rung is written
 * to the inbox first, and only a *newly created* row also raises the best-effort OS push. That
 * one Boolean is what retired `BudgetAlertStore` — the inbox row's existence is now the dedup
 * record, and because the ids embed the month, rung re-arming at month rollover falls out for
 * free instead of needing the store's explicit month-clear.
 */
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
    private val observeBudgetWarnThreshold: ObserveBudgetWarnThresholdUseCase,
    private val observeBudgetOverAlertsEnabled: ObserveBudgetOverAlertsEnabledUseCase,
    private val observeBudgetOverThreshold: ObserveBudgetOverThresholdUseCase,
    private val observeMutedBudgetLines: ObserveMutedBudgetLinesUseCase,
    private val overAlertBacklogStore: BudgetOverAlertBacklogStore,
    private val recordNotification: RecordNotificationUseCase,
    private val getRaisedNotificationIds: GetRaisedNotificationIdsUseCase,
    private val pruneExpiredNotifications: PruneExpiredNotificationsUseCase,
    private val presenter: SystemNotificationPresenter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Opportunistic retention sweep — this worker already runs after every sync, so the
        // 60-day window needs no scheduled job of its own (ADR-0053 decision 4, ADR-0012).
        runCatching { pruneExpiredNotifications() }

        // The Budgets category switch is a full gate: off means silent everywhere, no inbox row
        // and no push (ADR-0053 decision 5 / ADR-0054 decision 1). No seeding here — with the
        // inbox authoritative, flipping the switch back on surfaces the budgets that are
        // *actually* over right now rather than pretending the crossings never happened. The
        // opt-in `over` rung below is the one exception (ADR-0054 consequences).
        if (!observeBudgetAlertsEnabled().first()) return Result.success()

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
        val ownTransactions = AnalysisExclusion.retainAnalyzable(transactions, excludedIds) { it.categoryId }
        val combinedTransactionsFiltered =
            AnalysisExclusion.retainAnalyzable(combinedTransactions, excludedIds) { it.categoryId }

        // A muted budget line (ADR-0054 decisions 6-8) is excluded entirely — none of its three
        // rungs are even checked, matching "mute = total silence for that budget".
        val mutedLines = observeMutedBudgetLines().first()
        val checkedBudgets = budgets.excludingMuted(mutedLines)
        val checkedSharedBudgets = sharedBudgets.excludingMuted(mutedLines)

        val warnPercent = observeBudgetWarnThreshold().first()
        val overEnabled = observeBudgetOverAlertsEnabled().first()
        val overPercent = observeBudgetOverThreshold().first()

        // While the opt-in over rung is off, keep the backlog matched to whichever budgets are
        // over *right now* — so enabling it later doesn't blast the user with pre-existing
        // crossings (ADR-0054 consequences, "seed the over slot as fired while off").
        if (!overEnabled) {
            val overRungOnly = listOf(BudgetAlertSlot.OVER to overPercent)
            val currentlyOver = (
                checkBudgetAlerts(checkedBudgets, ownTransactions, emptySet(), currentCalendarMonth, rungs = overRungOnly) +
                    checkBudgetAlerts(checkedSharedBudgets, combinedTransactionsFiltered, emptySet(), currentCalendarMonth, rungs = overRungOnly)
                ).map { it.notificationId }.toSet()
            overAlertBacklogStore.sync(currentlyOver)
        }

        val rungs = CheckBudgetAlertsUseCase.rungs(warnPercent, if (overEnabled) overPercent else null)
        val overBacklog = if (overEnabled) overAlertBacklogStore.current() else emptySet()

        // One query for the whole category's already-raised ids (read, unread, and dismissed
        // alike — a dismissed alert must never fire again), instead of a round trip per candidate.
        val alreadyRaised = getRaisedNotificationIds(CheckBudgetAlertsUseCase.ID_PREFIX) + overBacklog
        val alerts = checkBudgetAlerts(
            budgets = checkedBudgets,
            transactions = ownTransactions,
            alreadyRaisedIds = alreadyRaised,
            currentMonth = currentCalendarMonth,
            rungs = rungs,
        ) + checkBudgetAlerts(
            budgets = checkedSharedBudgets,
            transactions = combinedTransactionsFiltered,
            alreadyRaisedIds = alreadyRaised,
            currentMonth = currentCalendarMonth,
            rungs = rungs,
        )

        for (alert in alerts) {
            val label = BudgetAlertCopy.label(
                alert.budget.categoryId?.let { categoryRepository.getCategory(it)?.name },
            )
            val title = BudgetAlertCopy.title(alert.slot, label, alert.spentPercent)
            val body = BudgetAlertCopy.body(alert.slot, label, alert.spentPercent)
            val created = recordNotification(
                id = alert.notificationId,
                category = NotificationCategory.BUDGET,
                title = title,
                body = body,
                deepLink = MainActivity.ROUTE_MANAGE,
            )
            // Push only on a genuinely new row: create-if-absent already collapsed the case where
            // this device re-detects a crossing, or where the partner's/web client's row for the
            // same rung arrived by sync first.
            if (created) {
                presenter.post(
                    AppNotification(
                        id = alert.notificationId,
                        category = NotificationCategory.BUDGET,
                        title = title,
                        body = body,
                        deepLink = MainActivity.ROUTE_MANAGE,
                        createdAt = now,
                        isRead = false,
                    ),
                )
            }
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "ipon_budget_alerts"

        fun buildRequest() = OneTimeWorkRequestBuilder<BudgetAlertWorker>().build()
    }
}

/** Drops any budget whose (category, scope) line is muted (ADR-0054 decisions 6-8). */
private fun List<Budget>.excludingMuted(mutedLines: Set<String>): List<Budget> =
    filterNot { BudgetLineId.of(it.categoryId, it.isShared) in mutedLines }
