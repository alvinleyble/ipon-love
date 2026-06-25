package com.iponlove.app.feature.budgets.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.iponlove.app.MainActivity
import com.iponlove.app.R
import com.iponlove.app.feature.budgets.domain.usecase.BudgetAlertResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Budget Alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Notifies when a budget reaches 80% or 100% of its limit." }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun fire(alert: BudgetAlertResult, categoryName: String?) {
        val budgetLabel = categoryName ?: "Overall Budget"
        val title = if (alert.threshold >= 100) {
            "$budgetLabel limit reached"
        } else {
            "$budgetLabel at ${alert.spentPercent}%"
        }
        val body = if (alert.threshold >= 100) {
            "You've hit your $budgetLabel limit for this month."
        } else {
            "You've used ${alert.spentPercent}% of your $budgetLabel limit."
        }
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = Math.abs(alert.dedupeKey.hashCode())
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_ID = "budget_alerts"
    }
}
