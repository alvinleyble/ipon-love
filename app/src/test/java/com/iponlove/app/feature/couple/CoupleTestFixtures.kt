package com.iponlove.app.feature.couple

import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.notes.domain.model.Note
import com.iponlove.app.feature.notes.domain.model.NoteAttachment
import com.iponlove.app.feature.notes.domain.repository.NoteAttachmentRepository
import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import com.iponlove.app.feature.savings.domain.model.GoalContribution
import com.iponlove.app.feature.savings.domain.model.SavingsGoal
import com.iponlove.app.feature.savings.domain.repository.GoalContributionRepository
import com.iponlove.app.feature.savings.domain.repository.SavingsGoalRepository
import java.math.BigDecimal
import com.iponlove.app.core.entitlement.Entitlement
import java.time.Instant
import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.repository.TransactionImageRepository
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import com.iponlove.app.feature.user.domain.model.User
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Shared fakes for the couple-feature use-case tests (purge + unpair watcher). */

internal class CountingAccountRepo : AccountRepository {
    var purgeCount = 0
    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> = emptyFlow()
    override suspend fun getAccount(id: String): Account? = null
    override suspend fun countOwnedAccounts(): Int = 0
    override suspend fun countSharedAccounts(): Int = 0
    override suspend fun upsertAccount(account: Account) = Unit
    override suspend fun reorderAccounts(orderedIds: List<String>) = Unit
    override suspend fun setArchived(id: String, archived: Boolean) = Unit
    override suspend fun deleteAccount(id: String) = Unit
    override suspend fun shareAccount(id: String, coupleId: String) = Unit
    override suspend fun unshareAccount(id: String) = Unit
    override suspend fun purgePartnerData() { purgeCount++ }
}

internal class CountingCategoryRepo : CategoryRepository {
    var purgeCount = 0
    override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> = emptyFlow()
    override fun observeAllCategories(): Flow<List<Category>> = emptyFlow()
    override suspend fun getCategory(id: String): Category? = null
    override suspend fun countOwnedCategories(): Int = 0
    override suspend fun countSharedCategories(): Int = 0
    override suspend fun upsertCategory(category: Category) = Unit
    override suspend fun reorderCategories(orderedIds: List<String>) = Unit
    override suspend fun setArchived(id: String, archived: Boolean) = Unit
    override suspend fun deleteCategory(id: String) = Unit
    override suspend fun shareCategory(id: String, coupleId: String) = Unit
    override suspend fun unshareCategory(id: String) = Unit
    override suspend fun purgePartnerData() { purgeCount++ }
}

internal class CountingTransactionRepo : TransactionRepository {
    var purgeCount = 0
    override fun observeTransactions(): Flow<List<Transaction>> = emptyFlow()
    override fun observeTransactions(startInclusive: Instant, endExclusive: Instant): Flow<List<Transaction>> =
        emptyFlow()
    override fun observeHasAnyTransaction(): Flow<Boolean> = emptyFlow()
    override fun observeCombinedTransactions(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<OwnedTransaction>> = emptyFlow()
    override fun observeHasAnyCombinedTransaction(): Flow<Boolean> = emptyFlow()
    override fun observeBalanceLedger(): Flow<List<Transaction>> = emptyFlow()
    override suspend fun getTransaction(id: String): Transaction? = null
    override suspend fun upsertTransaction(transaction: Transaction) = Unit
    override suspend fun deleteTransaction(id: String) = Unit
    override suspend fun materializeTransaction(transaction: Transaction, recurringRuleId: String) = false
    override suspend fun purgePartnerData() { purgeCount++ }
}

internal class CountingTransactionImageRepo : TransactionImageRepository {
    var purgeCount = 0
    override suspend fun getImages(transactionId: String): List<TransactionImage> = emptyList()
    override fun observeImageUrls(): Flow<Map<String, List<String>>> = emptyFlow()
    override suspend fun addImage(transactionId: String, imageId: String, localPath: String) = Unit
    override suspend fun deleteImage(id: String) = Unit
    override suspend fun purgePartnerData(userId: String) { purgeCount++ }
}

internal class CountingNoteRepo : NoteRepository {
    var purgeCount = 0
    override fun observeNotes(): Flow<List<Note>> = emptyFlow()
    override suspend fun getNote(id: String): Note? = null
    override suspend fun upsertNote(note: Note) = Unit
    override suspend fun deleteNote(id: String) = Unit
    override suspend fun shareNote(id: String, coupleId: String) = Unit
    override suspend fun setPinned(id: String, isPinned: Boolean) = Unit
    override suspend fun unshareNote(id: String) = Unit
    override suspend fun purgePartnerData() { purgeCount++ }
}

internal class CountingNoteAttachmentRepo : NoteAttachmentRepository {
    var purgeCount = 0
    override fun observeByNote(noteId: String): Flow<List<NoteAttachment>> = emptyFlow()
    override suspend fun addAttachment(noteId: String, localPath: String): NoteAttachment =
        NoteAttachment(id = "stub", noteId = noteId, localPath = localPath, url = null, position = 0)
    override suspend fun deleteAttachment(id: String) = Unit
    override suspend fun softDeleteAllForNote(noteId: String) = Unit
    override suspend fun purgePartnerData(userId: String) { purgeCount++ }
}

internal class CountingBudgetRepo : BudgetRepository {
    var purgeCount = 0
    override fun observeBudgets(): Flow<List<Budget>> = emptyFlow()
    override fun observeSharedBudgets(coupleId: String): Flow<List<Budget>> = emptyFlow()
    override suspend fun getBudget(id: String): Budget? = null
    override suspend fun countPersonalBudgets(yearMonth: String): Int = 0
    override suspend fun upsertBudget(budget: Budget) = Unit
    override suspend fun upsertSharedBudget(budget: Budget, coupleId: String) = Unit
    override suspend fun deleteBudget(id: String) = Unit
    override suspend fun purgeSharedBudgets() { purgeCount++ }
}

internal class CountingPartnerDebtRepo : PartnerDebtRepository {
    var purgeCount = 0
    override fun observeDebts(coupleId: String): Flow<List<PartnerDebt>> = emptyFlow()
    override fun observePayments(): Flow<List<DebtPayment>> = emptyFlow()
    override suspend fun getDebt(id: String): PartnerDebt? = null
    override suspend fun getActiveDebts(coupleId: String): List<PartnerDebt> = emptyList()
    override suspend fun getActivePayments(): List<DebtPayment> = emptyList()
    override suspend fun upsertDebt(debt: PartnerDebt, coupleId: String) = Unit
    override suspend fun deleteDebt(id: String) = Unit
    override suspend fun upsertPayment(payment: DebtPayment) = Unit
    override suspend fun stampReceiverTxn(paymentId: String, receiverTxnId: String) = Unit
    override suspend fun purgeCoupleDebts() { purgeCount++ }
}

internal class CountingSavingsGoalRepo : SavingsGoalRepository {
    var purgeCount = 0
    override fun observeGoals(): Flow<List<SavingsGoal>> = emptyFlow()
    override suspend fun getGoal(id: String): SavingsGoal? = null
    override suspend fun countPersonalGoals(): Int = 0
    override suspend fun countSharedGoals(): Int = 0
    override suspend fun upsertGoal(goal: SavingsGoal) = Unit
    override suspend fun setArchived(id: String, archived: Boolean) = Unit
    override suspend fun deleteGoal(id: String) = Unit
    override suspend fun shareGoal(id: String, coupleId: String) = Unit
    override suspend fun unshareGoal(id: String) = Unit
    override suspend fun purgePartnerData(userId: String) { purgeCount++ }
}

internal class CountingGoalContributionRepo : GoalContributionRepository {
    var purgeCount = 0
    override fun observeAllActive(): Flow<List<GoalContribution>> = emptyFlow()
    override fun observeByGoal(goalId: String): Flow<List<GoalContribution>> = emptyFlow()
    override suspend fun addContribution(goalId: String, amount: BigDecimal, date: Instant, note: String?) = Unit
    override suspend fun editContribution(id: String, amount: BigDecimal, date: Instant, note: String?) = Unit
    override suspend fun deleteContribution(id: String) = Unit
    override suspend fun softDeleteOwnForGoal(goalId: String) = Unit
    override suspend fun purgePartnerData(userId: String) { purgeCount++ }
}

/** A [UserRepository] whose current-user emissions the test drives via [currentUser]. */
internal class FakeUserFlowRepository(
    val currentUser: MutableStateFlow<User?>,
) : UserRepository {
    override fun observeCurrentUser(): Flow<User?> = currentUser
    override fun observePartner(coupleId: String): Flow<User?> = emptyFlow()
    override suspend fun ensureLocalRow(userId: String, displayName: String?) = Unit
    override suspend fun updateAccentColor(color: String) = Unit
    override suspend fun updateDisplayName(name: String) = Unit
    override suspend fun getSelfEntitlement(): Entitlement? = null
    override fun observeSelfEntitlement(): Flow<Entitlement> = emptyFlow()
    override fun observePartnerEntitlement(): Flow<Entitlement?> = emptyFlow()
    override suspend fun writeSelfEntitlement(entitlement: Entitlement, checkedAt: Instant) = Unit
}

internal fun userRow(coupleId: String?) =
    User(id = "user-1", displayName = "Pat", accentColor = "#FF0000", coupleId = coupleId)
