package com.iponlove.app.feature.partnerdebt.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.iponlove.app.MainActivity
import com.iponlove.app.feature.notifications.domain.model.AppNotification
import com.iponlove.app.feature.notifications.domain.model.NotificationCategory
import com.iponlove.app.feature.notifications.domain.usecase.GetRaisedNotificationIdsUseCase
import com.iponlove.app.feature.notifications.domain.usecase.RecordNotificationUseCase
import com.iponlove.app.feature.notifications.presentation.SystemNotificationPresenter
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtSeenStore
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import com.iponlove.app.feature.partnerdebt.domain.usecase.CheckPartnerDebtNotificationsUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.PartnerDebtNotificationCopy
import com.iponlove.app.feature.settings.domain.usecase.ObservePartnerDebtAlertsEnabledUseCase
import com.iponlove.app.feature.user.domain.repository.UserRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Sibling to [com.iponlove.app.feature.recurring.worker.RecurringReminderWorker] (Item 9 grill):
 * fires on app-open/sync, never a clock alarm. Notifies the current user only when their
 * *partner* authored a new debt with the user as borrower — writes an inbox row first (ADR-0053,
 * Way A); a genuinely new row also raises the best-effort OS push. [PartnerDebtSeenStore] keeps
 * a device's own authored debts (either direction) and any pre-existing backlog from ever firing.
 */
@HiltWorker
class PartnerDebtNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val userRepository: UserRepository,
    private val partnerDebtRepository: PartnerDebtRepository,
    private val observePartnerDebtAlertsEnabled: ObservePartnerDebtAlertsEnabledUseCase,
    private val checkPartnerDebtNotifications: CheckPartnerDebtNotificationsUseCase,
    private val seenStore: PartnerDebtSeenStore,
    private val recordNotification: RecordNotificationUseCase,
    private val getRaisedNotificationIds: GetRaisedNotificationIdsUseCase,
    private val presenter: SystemNotificationPresenter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!observePartnerDebtAlertsEnabled().first()) return Result.success()

        val me = userRepository.observeCurrentUser().first()
        val coupleId = me?.coupleId ?: return Result.success()

        val debts = partnerDebtRepository.getActiveDebts(coupleId)
        val seen = seenStore.snapshot(debts.map { it.id }.toSet())
        val alreadyRaised = getRaisedNotificationIds(CheckPartnerDebtNotificationsUseCase.ID_PREFIX)

        val due = checkPartnerDebtNotifications(debts, me.id, seen, alreadyRaised)
        if (due.isEmpty()) return Result.success()

        val partnerName = userRepository.observePartner(coupleId).first()?.displayName ?: "Your partner"
        val now = Instant.now()

        for (result in due) {
            val title = PartnerDebtNotificationCopy.title(partnerName)
            val body = PartnerDebtNotificationCopy.body(result.debt.amount)
            val created = recordNotification(
                id = result.notificationId,
                category = NotificationCategory.COUPLE,
                title = title,
                body = body,
                deepLink = MainActivity.ROUTE_COUPLE,
            )
            // Push only on a genuinely new row — mirrors BudgetAlertWorker's/RecurringReminderWorker's
            // guard against re-detection on another device/sync raising the same debt first.
            if (created) {
                presenter.post(
                    AppNotification(
                        id = result.notificationId,
                        category = NotificationCategory.COUPLE,
                        title = title,
                        body = body,
                        deepLink = MainActivity.ROUTE_COUPLE,
                        createdAt = now,
                        isRead = false,
                    ),
                )
            }
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "ipon_partner_debt_alerts"

        fun buildRequest() = OneTimeWorkRequestBuilder<PartnerDebtNotificationWorker>().build()
    }
}
