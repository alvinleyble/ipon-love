package com.iponlove.app.feature.export

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.export.domain.ExportDescription
import com.iponlove.app.feature.export.domain.model.ExportRow
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * The note → category → type fallback chain (v1.7.0 Item 6 decision 9). Every attachment surface
 * — claim-sheet row, receipt caption, ZIP filename — describes a transaction through this one
 * function, so the same row can never read one way on the sheet and another on its receipt page.
 */
class ExportDescriptionTest {

    private fun row(note: String, category: String, type: TransactionType = TransactionType.EXPENSE) =
        ExportRow(
            date = Instant.parse("2026-07-20T02:00:00Z"),
            type = type,
            category = category,
            account = "Cash",
            note = note,
            signedAmount = BigDecimal("-10.00"),
        )

    @Test
    fun `the note wins when there is one`() {
        assertThat(ExportDescription.of(row("Grab to client", "Transportation")))
            .isEqualTo("Grab to client")
    }

    @Test
    fun `falls back to the category when the note is empty`() {
        assertThat(ExportDescription.of(row("", "Transportation"))).isEqualTo("Transportation")
    }

    @Test
    fun `a whitespace-only note is treated as no note`() {
        assertThat(ExportDescription.of(row("   ", "Transportation"))).isEqualTo("Transportation")
    }

    @Test
    fun `falls back to the type when both note and category are empty`() {
        assertThat(ExportDescription.of(row("", "", TransactionType.TRANSFER))).isEqualTo("Transfer")
    }

    @Test
    fun `a note is trimmed, not passed through raw`() {
        assertThat(ExportDescription.of(row("  Lunch  ", "Food"))).isEqualTo("Lunch")
    }

    @Test
    fun `type labels match the CSV's spelling`() {
        assertThat(ExportDescription.typeLabel(TransactionType.INCOME)).isEqualTo("Income")
        assertThat(ExportDescription.typeLabel(TransactionType.EXPENSE)).isEqualTo("Expense")
        assertThat(ExportDescription.typeLabel(TransactionType.TRANSFER)).isEqualTo("Transfer")
    }
}
