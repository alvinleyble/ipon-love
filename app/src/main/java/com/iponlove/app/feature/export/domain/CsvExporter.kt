package com.iponlove.app.feature.export.domain

import com.iponlove.app.feature.export.domain.model.ExportRow
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Serialises resolved [ExportRow]s to a spreadsheet-friendly CSV (v1.7.0 Item 6, Slice 1). Pure —
 * no Android, no I/O — so the row shape and totalling are unit-testable.
 *
 * Shape (decision 9): header `Date · Type · Category · Account · Note · Amount · Receipts`, one row
 * per transaction, then a **Total** row. Amounts are bare, locale-independent numbers (`480.00` /
 * `-500.00`) so Excel/Sheets parse them as numbers, not text; the Total equals the exact column sum.
 * Fields are RFC-4180 escaped (quote-wrap + double any embedded quote) so notes/names with commas
 * survive.
 */
object CsvExporter {

    private const val HEADER = "Date,Type,Category,Account,Note,Amount,Receipts"

    fun build(rows: List<ExportRow>, zone: ZoneId = ZoneId.systemDefault()): String {
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(zone)
        val amountFmt = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))
        val sb = StringBuilder()
        sb.append(HEADER).append(LINE)
        var total = BigDecimal.ZERO
        for (row in rows) {
            total = total.add(row.signedAmount)
            sb.append(escape(dateFmt.format(row.date))).append(',')
            sb.append(escape(typeLabel(row.type))).append(',')
            sb.append(escape(row.category)).append(',')
            sb.append(escape(row.account)).append(',')
            sb.append(escape(row.note)).append(',')
            sb.append(amountFmt.format(row.signedAmount)).append(',')
            sb.append(row.receiptCount.toString()).append(LINE)
        }
        // Total row: label in the Date column, sum in the Amount column, others blank.
        sb.append("Total,,,,,").append(amountFmt.format(total)).append(',').append(LINE)
        return sb.toString()
    }

    private fun typeLabel(type: TransactionType): String = when (type) {
        TransactionType.INCOME -> "Income"
        TransactionType.EXPENSE -> "Expense"
        TransactionType.TRANSFER -> "Transfer"
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    private const val LINE = "\r\n"
}
