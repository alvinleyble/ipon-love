package com.iponlove.app.feature.export

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.export.domain.CsvExporter
import com.iponlove.app.feature.export.domain.model.ExportPhoto
import com.iponlove.app.feature.export.domain.model.ExportRow
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * Pure-function tests for the CSV writer (v1.7.0 Item 6, Slice 1). Covers the shape the grill
 * pinned: bare locale-independent amounts, signed-by-type, RFC-4180 escaping, and a Total row that
 * equals the exact column sum.
 */
class CsvExporterTest {

    private val zone = ZoneOffset.UTC

    private fun row(
        type: TransactionType,
        category: String,
        account: String = "BPI",
        note: String = "",
        signed: String,
        receipts: Int = 0,
        date: String = "2026-07-20T02:00:00Z",
    ) = ExportRow(
        date = Instant.parse(date),
        type = type,
        category = category,
        account = account,
        note = note,
        signedAmount = BigDecimal(signed),
        receipts = List(receipts) { ExportPhoto(id = "img$it", url = "https://example/$it.jpg") },
    )

    private fun lines(csv: String) = csv.trimEnd().split("\r\n")

    @Test
    fun `header is the fixed column order`() {
        val csv = CsvExporter.build(emptyList(), zone)
        assertThat(lines(csv).first()).isEqualTo("Date,Type,Category,Account,Note,Amount,Receipts")
    }

    @Test
    fun `income row is positive, expense row is negative`() {
        val csv = CsvExporter.build(
            listOf(
                row(TransactionType.INCOME, "Salary", signed = "20000.00"),
                row(TransactionType.EXPENSE, "Groceries", signed = "-500.00"),
            ),
            zone,
        )
        val body = lines(csv)
        assertThat(body[1]).isEqualTo("2026-07-20,Income,Salary,BPI,,20000.00,0")
        assertThat(body[2]).isEqualTo("2026-07-20,Expense,Groceries,BPI,,-500.00,0")
    }

    @Test
    fun `transfer destination and receipt count render`() {
        val csv = CsvExporter.build(
            listOf(row(TransactionType.TRANSFER, "→ Credit Card", signed = "-800.00", receipts = 2)),
            zone,
        )
        assertThat(lines(csv)[1]).isEqualTo("2026-07-20,Transfer,→ Credit Card,BPI,,-800.00,2")
    }

    @Test
    fun `amount is bare and locale-independent`() {
        val csv = CsvExporter.build(
            listOf(row(TransactionType.EXPENSE, "Big", signed = "-1234567.50")),
            zone,
        )
        // No thousands separators, no currency glyph — Sheets parses it as a number.
        assertThat(lines(csv)[1]).contains(",-1234567.50,")
    }

    @Test
    fun `fields with commas or quotes are escaped`() {
        val csv = CsvExporter.build(
            listOf(row(TransactionType.EXPENSE, "Groceries", note = "eggs, milk", signed = "-100.00")),
            zone,
        )
        assertThat(lines(csv)[1]).contains("\"eggs, milk\"")

        val quoted = CsvExporter.build(
            listOf(row(TransactionType.EXPENSE, "Groceries", note = "say \"hi\"", signed = "-100.00")),
            zone,
        )
        assertThat(lines(quoted)[1]).contains("\"say \"\"hi\"\"\"")
    }

    @Test
    fun `total row equals the sum of the amount column`() {
        val csv = CsvExporter.build(
            listOf(
                row(TransactionType.INCOME, "Salary", signed = "20000.00"),
                row(TransactionType.EXPENSE, "Groceries", signed = "-500.00"),
                row(TransactionType.TRANSFER, "→ Card", signed = "-800.00"),
            ),
            zone,
        )
        val total = lines(csv).last()
        // 20000 - 500 - 800 = 18700
        assertThat(total).isEqualTo("Total,,,,,18700.00,")
    }

    @Test
    fun `empty export still has header and a zero total`() {
        val csv = CsvExporter.build(emptyList(), zone)
        val body = lines(csv)
        assertThat(body).hasSize(2)
        assertThat(body.last()).isEqualTo("Total,,,,,0.00,")
    }
}
