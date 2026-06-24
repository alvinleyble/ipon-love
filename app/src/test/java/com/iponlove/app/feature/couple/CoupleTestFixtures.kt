package com.iponlove.app.feature.couple

import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.notes.domain.model.Note
import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
import com.iponlove.app.feature.transactions.domain.model.Transaction
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
    override suspend fun upsertAccount(account: Account) = Unit
    override suspend fun setArchived(id: String, archived: Boolean) = Unit
    override suspend fun deleteAccount(id: String) = Unit
    override suspend fun purgePartnerData() { purgeCount++ }
}

internal class CountingCategoryRepo : CategoryRepository {
    var purgeCount = 0
    override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> = emptyFlow()
    override fun observeAllCategories(): Flow<List<Category>> = emptyFlow()
    override suspend fun getCategory(id: String): Category? = null
    override suspend fun upsertCategory(category: Category) = Unit
    override suspend fun setArchived(id: String, archived: Boolean) = Unit
    override suspend fun deleteCategory(id: String) = Unit
    override suspend fun purgePartnerData() { purgeCount++ }
}

internal class CountingTransactionRepo : TransactionRepository {
    var purgeCount = 0
    override fun observeTransactions(): Flow<List<Transaction>> = emptyFlow()
    override fun observeCombinedTransactions(): Flow<List<OwnedTransaction>> = emptyFlow()
    override suspend fun getTransaction(id: String): Transaction? = null
    override suspend fun upsertTransaction(transaction: Transaction) = Unit
    override suspend fun deleteTransaction(id: String) = Unit
    override suspend fun materializeTransaction(transaction: Transaction, recurringRuleId: String) = false
    override suspend fun purgePartnerData() { purgeCount++ }
}

internal class CountingNoteRepo : NoteRepository {
    var purgeCount = 0
    override fun observeNotes(): Flow<List<Note>> = emptyFlow()
    override suspend fun getNote(id: String): Note? = null
    override suspend fun upsertNote(note: Note) = Unit
    override suspend fun deleteNote(id: String) = Unit
    override suspend fun purgePartnerData() { purgeCount++ }
}

/** A [UserRepository] whose current-user emissions the test drives via [currentUser]. */
internal class FakeUserFlowRepository(
    val currentUser: MutableStateFlow<User?>,
) : UserRepository {
    override fun observeCurrentUser(): Flow<User?> = currentUser
    override fun observePartner(coupleId: String): Flow<User?> = emptyFlow()
    override suspend fun ensureLocalRow(userId: String) = Unit
}

internal fun userRow(coupleId: String?) =
    User(id = "user-1", displayName = "Pat", accentColor = "#FF0000", coupleId = coupleId)
