package com.iponlove.app.feature.couple

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.couple.domain.usecase.PurgePartnerReplicaUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PurgePartnerReplicaUseCaseTest {

    private val accounts = CountingAccountRepo()
    private val categories = CountingCategoryRepo()
    private val transactions = CountingTransactionRepo()
    private val notes = CountingNoteRepo()
    private val budgets = CountingBudgetRepo()
    private val partnerDebts = CountingPartnerDebtRepo()
    private val cursors = InMemoryCursorStore()
    private val useCase = PurgePartnerReplicaUseCase(
        accounts, categories, transactions, notes, budgets, partnerDebts, cursors,
    )

    @Test
    fun purges_everyPartnerTable_once() = runTest {
        useCase()

        assertThat(accounts.purgeCount).isEqualTo(1)
        assertThat(categories.purgeCount).isEqualTo(1)
        assertThat(transactions.purgeCount).isEqualTo(1)
        assertThat(notes.purgeCount).isEqualTo(1)
        assertThat(budgets.purgeCount).isEqualTo(1)
        assertThat(partnerDebts.purgeCount).isEqualTo(1)
    }

    @Test
    fun resets_allPartnerCursorsToZero() = runTest {
        // Seed non-zero cursors as a previous couple would have left them.
        cursors.setCursor(SyncTable.PARTNER_ACCOUNTS, 50)
        cursors.setCursor(SyncTable.PARTNER_CATEGORIES, 60)
        cursors.setCursor(SyncTable.PARTNER_TRANSACTIONS, 70)
        cursors.setCursor(SyncTable.PARTNER_NOTES, 80)

        useCase()

        assertThat(cursors.cursor(SyncTable.PARTNER_ACCOUNTS)).isEqualTo(0)
        assertThat(cursors.cursor(SyncTable.PARTNER_CATEGORIES)).isEqualTo(0)
        assertThat(cursors.cursor(SyncTable.PARTNER_TRANSACTIONS)).isEqualTo(0)
        assertThat(cursors.cursor(SyncTable.PARTNER_NOTES)).isEqualTo(0)
    }
}
