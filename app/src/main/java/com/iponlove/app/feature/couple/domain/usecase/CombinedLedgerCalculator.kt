package com.iponlove.app.feature.couple.domain.usecase

import com.iponlove.app.feature.couple.domain.model.CombinedEntry
import com.iponlove.app.feature.couple.domain.model.CombinedLedger
import com.iponlove.app.feature.couple.domain.model.MemberSpend
import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.user.domain.model.User
import java.math.BigDecimal
import java.time.Instant

/**
 * Builds the [CombinedLedger] from the already-merged, owner-tagged ledger. Pure (no clock,
 * no I/O) so the merge and the per-member monthly-spend math stay deterministic and
 * testable; the caller supplies the half-open month window [monthStartInclusive,
 * monthEndExclusive) and the live category-name map.
 *
 * [transactions] is assumed pre-filtered to shared, non-deleted rows and pre-sorted by the
 * query (date desc); the entry order is preserved. Spend counts EXPENSE only — TRANSFER,
 * INCOME, and settlement legs are not spending, consistent with AnalysisCalculator.
 */
object CombinedLedgerCalculator {

    fun analyze(
        transactions: List<OwnedTransaction>,
        categoryNames: Map<String, String>,
        me: User,
        partner: User?,
        monthStartInclusive: Instant,
        monthEndExclusive: Instant,
    ): CombinedLedger {
        val entries = transactions.filter { it.transaction.type != TransactionType.TRANSFER }.map { owned ->
            val t = owned.transaction
            CombinedEntry(
                id = t.id,
                ownerId = owned.ownerId,
                type = t.type,
                amount = t.amount,
                title = titleFor(t, categoryNames),
                date = t.date,
                isMine = owned.ownerId == me.id,
                attachmentUrl = t.attachmentUrl,
            )
        }

        val members = buildList {
            add(spendFor(me, isMine = true, transactions, monthStartInclusive, monthEndExclusive))
            if (partner != null) {
                add(spendFor(partner, isMine = false, transactions, monthStartInclusive, monthEndExclusive))
            }
        }

        return CombinedLedger(entries = entries, members = members)
    }

    private fun titleFor(t: Transaction, names: Map<String, String>): String = when {
        // Settlement legs carry categoryId = null + isSettlement = true by design (ADR-0019 #14 /
        // ADR-0042); label them off the flag instead of falling to "Uncategorized", matching Records.
        t.isSettlement -> "Debt settlement"
        t.type == TransactionType.TRANSFER -> "Transfer"
        else -> t.categoryId?.let { names[it] } ?: "Uncategorized"
    }

    private fun spendFor(
        user: User,
        isMine: Boolean,
        transactions: List<OwnedTransaction>,
        startInclusive: Instant,
        endExclusive: Instant,
    ): MemberSpend {
        var expense = BigDecimal.ZERO
        for (owned in transactions) {
            if (owned.ownerId != user.id) continue
            val t = owned.transaction
            if (t.type != TransactionType.EXPENSE) continue
            if (t.isSettlement) continue // repayment legs aren't spend — consistent with the Analysis calculators
            if (t.date < startInclusive || t.date >= endExclusive) continue
            expense += t.amount
        }
        return MemberSpend(
            id = user.id,
            displayName = user.displayName,
            accentColor = user.accentColor,
            isMine = isMine,
            monthlyExpense = expense,
        )
    }
}
