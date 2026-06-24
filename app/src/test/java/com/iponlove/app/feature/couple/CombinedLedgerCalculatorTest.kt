package com.iponlove.app.feature.couple

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.couple.domain.usecase.CombinedLedgerCalculator
import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.txn
import com.iponlove.app.feature.user.domain.model.User
import java.math.BigDecimal
import java.time.Instant
import org.junit.Test

/**
 * The combined view (ADR-0011): a merged, owner-attributed stream + each member's monthly
 * EXPENSE. Pure aggregation — privacy/redaction is enforced upstream (the calculator only
 * ever receives shared rows), so these cases cover attribution, titles, and the spend math.
 */
class CombinedLedgerCalculatorTest {

    private val me = User(id = "me", displayName = "Alvin", accentColor = "#FF0000", coupleId = "c-1")
    private val partner = User(id = "you", displayName = "Patty", accentColor = "#00FF00", coupleId = "c-1")

    private val monthStart = Instant.parse("2026-06-01T00:00:00Z")
    private val monthEnd = Instant.parse("2026-07-01T00:00:00Z")
    private fun june(day: Int) = Instant.parse("2026-06-${"%02d".format(day)}T12:00:00Z")

    private fun owned(ownerId: String, t: com.iponlove.app.feature.transactions.domain.model.Transaction) =
        OwnedTransaction(ownerId = ownerId, transaction = t)

    @Test
    fun mergesBothOwners_preservingInputOrder_withAttributionAndTitles() {
        val transactions = listOf(
            owned("me", txn("t1", TransactionType.EXPENSE, "100.00", categoryId = "cat-food", date = june(5))),
            owned("you", txn("t2", TransactionType.EXPENSE, "200.00", categoryId = "cat-gas", date = june(4))),
            owned("you", txn("t3", TransactionType.TRANSFER, "50.00", accountId = "a", toAccountId = "b", date = june(3))),
            owned("me", txn("t4", TransactionType.EXPENSE, "30.00", categoryId = null, date = june(2))),
        )

        val ledger = CombinedLedgerCalculator.analyze(
            transactions = transactions,
            categoryNames = mapOf("cat-food" to "Food", "cat-gas" to "Gas"),
            me = me,
            partner = partner,
            monthStartInclusive = monthStart,
            monthEndExclusive = monthEnd,
        )

        // Input order is preserved (the query already sorted by date desc).
        assertThat(ledger.entries.map { it.id }).containsExactly("t1", "t2", "t3", "t4").inOrder()
        assertThat(ledger.entries.map { it.isMine }).containsExactly(true, false, false, true).inOrder()
        // Titles: category name, transfer label, and the uncategorized fallback.
        assertThat(ledger.entries.map { it.title })
            .containsExactly("Food", "Gas", "Transfer", "Uncategorized").inOrder()
        assertThat(ledger.entries[1].ownerId).isEqualTo("you")
    }

    @Test
    fun monthlySpend_sumsExpenseOnly_perMember_withinWindow() {
        val transactions = listOf(
            owned("me", txn("m1", TransactionType.EXPENSE, "100.00", date = june(5))),
            owned("me", txn("m2", TransactionType.EXPENSE, "50.00", date = june(6))),
            owned("me", txn("m3", TransactionType.INCOME, "9000.00", date = june(7))),       // income ignored
            owned("me", txn("m4", TransactionType.TRANSFER, "20.00", accountId = "a", toAccountId = "b", date = june(8))), // transfer ignored
            owned("you", txn("p1", TransactionType.EXPENSE, "300.00", date = june(5))),
        )

        val ledger = CombinedLedgerCalculator.analyze(
            transactions, emptyMap(), me, partner, monthStart, monthEnd,
        )

        val mine = ledger.members.single { it.isMine }
        val theirs = ledger.members.single { !it.isMine }
        assertThat(mine.monthlyExpense).isEqualTo(BigDecimal("150.00"))
        assertThat(theirs.monthlyExpense).isEqualTo(BigDecimal("300.00"))
        assertThat(mine.accentColor).isEqualTo("#FF0000")
        assertThat(theirs.displayName).isEqualTo("Patty")
    }

    @Test
    fun monthlySpend_excludesTransactionsOutsideTheWindow() {
        val transactions = listOf(
            owned("me", txn("before", TransactionType.EXPENSE, "999.00", date = Instant.parse("2026-05-31T23:59:59Z"))),
            owned("me", txn("in", TransactionType.EXPENSE, "100.00", date = monthStart)),       // start is inclusive
            owned("me", txn("after", TransactionType.EXPENSE, "999.00", date = monthEnd)),       // end is exclusive
        )

        val ledger = CombinedLedgerCalculator.analyze(
            transactions, emptyMap(), me, partner, monthStart, monthEnd,
        )

        assertThat(ledger.members.single { it.isMine }.monthlyExpense).isEqualTo(BigDecimal("100.00"))
    }

    @Test
    fun withoutPartner_onlyMyChipIsEmitted_butStreamStillMerges() {
        val transactions = listOf(
            owned("me", txn("t1", TransactionType.EXPENSE, "100.00", date = june(5))),
        )

        val ledger = CombinedLedgerCalculator.analyze(
            transactions, emptyMap(), me, partner = null, monthStartInclusive = monthStart, monthEndExclusive = monthEnd,
        )

        assertThat(ledger.members).hasSize(1)
        assertThat(ledger.members.single().isMine).isTrue()
        assertThat(ledger.entries).hasSize(1)
    }
}
