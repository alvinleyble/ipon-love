package com.iponlove.app.feature.export.domain

import com.iponlove.app.feature.export.domain.model.ExportRow
import com.iponlove.app.feature.transactions.domain.model.TransactionType

/**
 * How a transaction is described on a PDF claim sheet, a receipt caption, and inside a ZIP
 * filename (v1.7.0 Item 6 decision 9): the user's own **note** if there is one, else the
 * **category** label, else the bare **type**. Pure, and shared by every writer so the same
 * transaction never reads one way on the claim sheet and another on its receipt page.
 */
object ExportDescription {

    fun of(row: ExportRow): String = when {
        row.note.isNotBlank() -> row.note.trim()
        row.category.isNotBlank() -> row.category
        else -> typeLabel(row.type)
    }

    /** The one place a [TransactionType] is spelled for a human — every export format shares it. */
    fun typeLabel(type: TransactionType): String = when (type) {
        TransactionType.INCOME -> "Income"
        TransactionType.EXPENSE -> "Expense"
        TransactionType.TRANSFER -> "Transfer"
    }
}
