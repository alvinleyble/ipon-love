package com.iponlove.app.feature.export.domain.model

import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant

/**
 * One transaction resolved for export (v1.7.0 Item 6): account/category ids looked up to names,
 * the amount signed by [type] (income +, expense/transfer −), and its receipt photos attached.
 * The pure output of [ExportRowMapper] — the CSV, PDF and ZIP writers all format from this, never
 * from a raw [Transaction].
 */
data class ExportRow(
    val date: Instant,
    val type: TransactionType,
    /** Resolved label: the category name; `→ Destination` for a transfer; `Debt settlement` for a
     *  settlement leg; `Balance adjustment` for a correction row; `Uncategorized` when a
     *  categorised row has lost its category. */
    val category: String,
    /** The source account name (a transfer's *from* account). */
    val account: String,
    /** The raw note, or empty. */
    val note: String,
    /** Signed magnitude: income positive, expense/transfer negative — so a spreadsheet column-sum
     *  equals the CSV's own Total row. */
    val signedAmount: BigDecimal,
    /** This row's receipt photos, in display order (Slice 2 bundles them; the CSV only counts them). */
    val receipts: List<ExportPhoto> = emptyList(),
) {
    /** The CSV's `Receipts` column. Counts pre-upload photos too — they are just as exportable. */
    val receiptCount: Int get() = receipts.size
}

/**
 * Maps a [Transaction] to an [ExportRow]. Pure and Android-free so the export shape (category-less
 * row labelling, sign-by-type) is unit-testable without Hilt — mirrors the Records
 * `Transaction.toListItem` label branches (transfer → destination, settlement → "Debt settlement",
 * adjustment → "Balance adjustment").
 */
object ExportRowMapper {
    fun toRow(
        transaction: Transaction,
        accountNames: Map<String, String>,
        categoryNames: Map<String, String>,
        receipts: List<ExportPhoto> = emptyList(),
    ): ExportRow {
        val account = accountNames[transaction.accountId] ?: "Account"
        val category = when {
            transaction.type == TransactionType.TRANSFER ->
                "→ " + (transaction.toAccountId?.let { accountNames[it] } ?: "Account")
            transaction.isSettlement -> "Debt settlement"
            transaction.isAdjustment -> "Balance adjustment"
            else -> transaction.categoryId?.let { categoryNames[it] } ?: "Uncategorized"
        }
        val signed = when (transaction.type) {
            TransactionType.INCOME -> transaction.amount
            TransactionType.EXPENSE -> transaction.amount.negate()
            TransactionType.TRANSFER -> transaction.amount.negate()
        }
        return ExportRow(
            date = transaction.date,
            type = transaction.type,
            category = category,
            account = account,
            note = transaction.note.orEmpty(),
            signedAmount = signed,
            receipts = receipts,
        )
    }
}
