package com.iponlove.app.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.iponlove.app.core.analytics.local.AnalyticsEventDao
import com.iponlove.app.core.analytics.local.AnalyticsEventEntity
import com.iponlove.app.core.config.local.AppConfigDao
import com.iponlove.app.core.config.local.AppConfigEntity
import com.iponlove.app.core.database.converters.IponConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.iponlove.app.feature.accounts.data.local.AccountDao
import com.iponlove.app.feature.accounts.data.local.AccountEntity
import com.iponlove.app.feature.budgets.data.local.BudgetDao
import com.iponlove.app.feature.budgets.data.local.BudgetEntity
import com.iponlove.app.feature.categories.data.local.CategoryDao
import com.iponlove.app.feature.categories.data.local.CategoryEntity
import com.iponlove.app.feature.couple.data.local.CoupleDao
import com.iponlove.app.feature.couple.data.local.CoupleEntity
import com.iponlove.app.feature.notes.data.local.NoteAttachmentDao
import com.iponlove.app.feature.notes.data.local.NoteAttachmentEntity
import com.iponlove.app.feature.notes.data.local.NoteDao
import com.iponlove.app.feature.notes.data.local.NoteEntity
import com.iponlove.app.feature.partnerdebt.data.local.DebtPaymentEntity
import com.iponlove.app.feature.partnerdebt.data.local.PartnerDebtDao
import com.iponlove.app.feature.partnerdebt.data.local.PartnerDebtEntity
import com.iponlove.app.feature.recurring.data.local.RecurringRuleDao
import com.iponlove.app.feature.recurring.data.local.RecurringRuleEntity
import com.iponlove.app.feature.savings.data.local.GoalContributionDao
import com.iponlove.app.feature.savings.data.local.GoalContributionEntity
import com.iponlove.app.feature.savings.data.local.SavingsGoalDao
import com.iponlove.app.feature.savings.data.local.SavingsGoalEntity
import com.iponlove.app.feature.transactions.data.local.TransactionDao
import com.iponlove.app.feature.transactions.data.local.TransactionEntity
import com.iponlove.app.feature.transactions.data.local.TransactionImageDao
import com.iponlove.app.feature.transactions.data.local.TransactionImageEntity
import com.iponlove.app.feature.user.data.local.UserDao
import com.iponlove.app.feature.user.data.local.UserEntity

/**
 * The single offline-first Room database — source of truth for the whole app
 * (ARCHITECTURE §6). Each feature slice adds its entity + DAO here.
 *
 * Schema is unstable during development, so we destructive-migrate; real migrations
 * land before the first release. Bump [version] whenever the entity set changes.
 */
@Database(
    entities = [
        UserEntity::class,
        CoupleEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionImageEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        NoteEntity::class,
        NoteAttachmentEntity::class,
        PartnerDebtEntity::class,
        DebtPaymentEntity::class,
        SavingsGoalEntity::class,
        GoalContributionEntity::class,
        AppConfigEntity::class,
        AnalyticsEventEntity::class,
    ],
    version = 25,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        // v23: transaction_images table auto-created; single-photo columns dropped (spec).
        AutoMigration(from = 22, to = 23, spec = DeleteReceiptColumnsMigration::class),
        // v24: users entitlement columns + app_config cache table (dormant paywall infra, S1).
        AutoMigration(from = 23, to = 24),
        // v25: analytics_events buffer table (paywall telemetry, S6).
        AutoMigration(from = 24, to = 25),
    ],
)
@TypeConverters(IponConverters::class)
abstract class IponDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun coupleDao(): CoupleDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transactionImageDao(): TransactionImageDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun noteDao(): NoteDao
    abstract fun noteAttachmentDao(): NoteAttachmentDao
    abstract fun partnerDebtDao(): PartnerDebtDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun goalContributionDao(): GoalContributionDao
    abstract fun appConfigDao(): AppConfigDao
    abstract fun analyticsEventDao(): AnalyticsEventDao

    /**
     * Wipe every table — the local source of truth for one account. Called on sign-out and on
     * the login account-switch guard so one user's offline-first data can never surface under
     * another's session (ADR-0021). Runs off the main thread; [clearAllTables] is blocking.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) { clearAllTables() }
}
