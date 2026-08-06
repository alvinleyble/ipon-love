package com.iponlove.app.core.di

import android.content.Context
import androidx.room.Room
import com.iponlove.app.core.analytics.local.AnalyticsEventDao
import com.iponlove.app.core.config.local.AppConfigDao
import com.iponlove.app.core.database.IponDatabase
import com.iponlove.app.feature.accounts.data.local.AccountDao
import com.iponlove.app.feature.budgets.data.local.BudgetDao
import com.iponlove.app.feature.categories.data.local.CategoryDao
import com.iponlove.app.feature.couple.data.local.CoupleDao
import com.iponlove.app.feature.drafts.data.local.TransactionDraftDao
import com.iponlove.app.feature.notes.data.local.NoteAttachmentDao
import com.iponlove.app.feature.notes.data.local.NoteDao
import com.iponlove.app.feature.notifications.data.local.NotificationDao
import com.iponlove.app.feature.partnerdebt.data.local.PartnerDebtDao
import com.iponlove.app.feature.recurring.data.local.RecurringRuleDao
import com.iponlove.app.feature.savings.data.local.GoalContributionDao
import com.iponlove.app.feature.savings.data.local.SavingsGoalDao
import com.iponlove.app.feature.transactions.data.local.TransactionDao
import com.iponlove.app.feature.transactions.data.local.TransactionImageDao
import com.iponlove.app.feature.user.data.local.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): IponDatabase =
        Room.databaseBuilder(context, IponDatabase::class.java, "ipon.db")
            .build()

    @Provides
    fun userDao(database: IponDatabase): UserDao = database.userDao()

    @Provides
    fun coupleDao(database: IponDatabase): CoupleDao = database.coupleDao()

    @Provides
    fun accountDao(database: IponDatabase): AccountDao = database.accountDao()

    @Provides
    fun categoryDao(database: IponDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun transactionDao(database: IponDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun transactionImageDao(database: IponDatabase): TransactionImageDao = database.transactionImageDao()

    @Provides
    fun budgetDao(database: IponDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun recurringRuleDao(database: IponDatabase): RecurringRuleDao = database.recurringRuleDao()

    @Provides
    fun noteDao(database: IponDatabase): NoteDao = database.noteDao()

    @Provides
    fun noteAttachmentDao(database: IponDatabase): NoteAttachmentDao = database.noteAttachmentDao()

    @Provides
    fun partnerDebtDao(database: IponDatabase): PartnerDebtDao = database.partnerDebtDao()

    @Provides
    fun savingsGoalDao(database: IponDatabase): SavingsGoalDao = database.savingsGoalDao()

    @Provides
    fun goalContributionDao(database: IponDatabase): GoalContributionDao =
        database.goalContributionDao()

    @Provides
    fun appConfigDao(database: IponDatabase): AppConfigDao = database.appConfigDao()

    @Provides
    fun analyticsEventDao(database: IponDatabase): AnalyticsEventDao = database.analyticsEventDao()

    @Provides
    fun notificationDao(database: IponDatabase): NotificationDao = database.notificationDao()

    @Provides
    fun transactionDraftDao(database: IponDatabase): TransactionDraftDao =
        database.transactionDraftDao()
}
