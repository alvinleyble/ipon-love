package com.iponlove.app.feature.notifications.presentation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iponlove.app.MainActivity
import com.iponlove.app.R
import com.iponlove.app.feature.notifications.domain.model.AppNotification
import com.iponlove.app.feature.notifications.domain.model.NotificationCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raises the **best-effort OS system-tray push** for an inbox row (ADR-0053, Way A: the inbox
 * row is the source of truth, this is the courtesy on top). Generalised out of the old
 * `BudgetAlertNotifier` so every category — budgets today, recurring reminders and partner-debt
 * alerts next — posts through one presenter instead of each growing its own notifier.
 *
 * One OS channel per category, so a user can silence a class of notification from system
 * settings independently of the in-app switches. The budget channel keeps its original
 * `budget_alerts` id, so a user who already tuned it doesn't get a fresh default channel.
 */
@Singleton
class SystemNotificationPresenter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Registers every category's channel. Called once per process from `IponApp.onCreate`. */
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        for (category in NotificationCategory.entries) {
            val (name, description) = channelCopy(category)
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId(category),
                    name,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { this.description = description },
            )
        }
    }

    /**
     * Posts [notification] to the tray. Grouped per category so a burst (a payday's worth of
     * recurring reminders, several budgets crossing at once) collapses into one tray stack
     * instead of a wall of separate notifications.
     */
    fun post(notification: AppNotification) {
        // Best-effort per ADR-0053: the inbox row is already written, so a denied
        // POST_NOTIFICATIONS (runtime-granted from API 33) just means no tray courtesy.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val tapIntent = PendingIntent.getActivity(
            context,
            notification.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                notification.deepLink?.let { putExtra(MainActivity.EXTRA_START_ROUTE, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val built = NotificationCompat.Builder(context, channelId(notification.category))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setContentIntent(tapIntent)
            .setGroup(channelId(notification.category))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(notification.id.hashCode(), built)
    }

    private fun channelId(category: NotificationCategory): String = when (category) {
        // Unchanged from the pre-inbox notifier so existing per-channel settings survive.
        NotificationCategory.BUDGET -> "budget_alerts"
        NotificationCategory.RECURRING -> "recurring_reminders"
        NotificationCategory.COUPLE -> "partner_debts"
        NotificationCategory.OTHER -> "general"
    }

    private fun channelCopy(category: NotificationCategory): Pair<String, String> =
        when (category) {
            NotificationCategory.BUDGET ->
                "Budget Alerts" to "Notifies when a budget approaches or passes its limit."
            NotificationCategory.RECURRING ->
                "Recurring reminders" to "Reminds you to confirm a recurring income or bill on its due date."
            NotificationCategory.COUPLE ->
                "Partner debts" to "Notifies when your partner logs a new debt you owe."
            NotificationCategory.OTHER ->
                "General" to "Other notifications from Love, Ipon."
        }
}
