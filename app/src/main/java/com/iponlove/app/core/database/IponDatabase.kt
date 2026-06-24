package com.iponlove.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.iponlove.app.core.database.converters.IponConverters
import com.iponlove.app.feature.accounts.data.local.AccountDao
import com.iponlove.app.feature.accounts.data.local.AccountEntity
import com.iponlove.app.feature.budgets.data.local.BudgetDao
import com.iponlove.app.feature.budgets.data.local.BudgetEntity
import com.iponlove.app.feature.categories.data.local.CategoryDao
import com.iponlove.app.feature.categories.data.local.CategoryEntity
import com.iponlove.app.feature.notes.data.local.NoteDao
import com.iponlove.app.feature.notes.data.local.NoteEntity
import com.iponlove.app.feature.recurring.data.local.RecurringRuleDao
import com.iponlove.app.feature.recurring.data.local.RecurringRuleEntity
import com.iponlove.app.feature.transactions.data.local.TransactionDao
import com.iponlove.app.feature.transactions.data.local.TransactionEntity

/**
 * The single offline-first Room database — source of truth for the whole app
 * (ARCHITECTURE §6). Each feature slice adds its entity + DAO here.
 *
 * Schema is unstable during development, so we destructive-migrate; real migrations
 * land before the first release. Bump [version] whenever the entity set changes.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        NoteEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(IponConverters::class)
abstract class IponDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun noteDao(): NoteDao
}
