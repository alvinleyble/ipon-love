package com.iponlove.app.feature.export

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.export.domain.ReceiptNumbering
import com.iponlove.app.feature.export.domain.model.ExportPhoto
import com.iponlove.app.feature.export.domain.model.ExportRow
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * The row↔photo linkage a PDF claim sheet and a ZIP both hang off (v1.7.0 Item 6 Slice 2,
 * decision 9). This is the load-bearing pure logic of the slice: get the numbering wrong and a
 * receipt page points at the wrong transaction, which is exactly the failure a reimbursement claim
 * cannot survive.
 */
class ReceiptNumberingTest {

    private fun row(note: String, photos: Int) = ExportRow(
        date = Instant.parse("2026-07-20T02:00:00Z"),
        type = TransactionType.EXPENSE,
        category = "Transportation",
        account = "Cash",
        note = note,
        signedAmount = BigDecimal("-100.00"),
        receipts = List(photos) { ExportPhoto(id = "$note-$it", url = "https://example/$note$it.jpg") },
    )

    @Test
    fun `only receipt-bearing rows are numbered, in row order`() {
        val numbered = ReceiptNumbering.of(
            listOf(row("no photo", 0), row("has photo", 1), row("also none", 0), row("second photo", 1)),
        )
        assertThat(numbered.rows.map { it.marker }).containsExactly(null, 1, null, 2).inOrder()
    }

    @Test
    fun `a single-photo row labels it plainly, with no letter suffix`() {
        val numbered = ReceiptNumbering.of(listOf(row("solo", 1)))
        assertThat(numbered.receipts).hasSize(1)
        assertThat(numbered.receipts.single().label).isEqualTo("#1")
    }

    @Test
    fun `a three-photo row suffixes a, b, c under one marker`() {
        val numbered = ReceiptNumbering.of(listOf(row("skip", 0), row("triple", 3)))
        assertThat(numbered.receipts.map { it.label }).containsExactly("#1a", "#1b", "#1c").inOrder()
        assertThat(numbered.receipts.map { it.marker }).containsExactly(1, 1, 1)
    }

    @Test
    fun `ordinals run across the whole export, not per row`() {
        val numbered = ReceiptNumbering.of(listOf(row("first", 2), row("second", 1)))
        assertThat(numbered.receipts.map { it.ordinal }).containsExactly(1, 2, 3).inOrder()
        assertThat(numbered.receipts.map { it.label }).containsExactly("#1a", "#1b", "#2").inOrder()
    }

    @Test
    fun `photoCount counts photos, not rows`() {
        val numbered = ReceiptNumbering.of(listOf(row("a", 3), row("b", 0), row("c", 2)))
        assertThat(numbered.photoCount).isEqualTo(5)
        assertThat(numbered.rows).hasSize(3)
    }

    @Test
    fun `downloadCount counts only photos that are not already on local disk`() {
        val local = ExportRow(
            date = Instant.parse("2026-07-20T02:00:00Z"),
            type = TransactionType.EXPENSE,
            category = "Food",
            account = "Cash",
            note = "mixed",
            signedAmount = BigDecimal("-10.00"),
            receipts = listOf(
                ExportPhoto(id = "pending", localPath = "/files/receipts/pending.jpg"),
                ExportPhoto(id = "uploaded", url = "https://example/uploaded.jpg"),
            ),
        )
        val numbered = ReceiptNumbering.of(listOf(local))
        assertThat(numbered.photoCount).isEqualTo(2)
        assertThat(numbered.downloadCount).isEqualTo(1)
    }

    @Test
    fun `an export with no photos numbers nothing`() {
        val numbered = ReceiptNumbering.of(listOf(row("a", 0), row("b", 0)))
        assertThat(numbered.receipts).isEmpty()
        assertThat(numbered.rows.map { it.marker }).containsExactly(null, null)
        assertThat(numbered.downloadCount).isEqualTo(0)
    }

    @Test
    fun `an empty export is empty, not an error`() {
        val numbered = ReceiptNumbering.of(emptyList())
        assertThat(numbered.rows).isEmpty()
        assertThat(numbered.receipts).isEmpty()
        assertThat(numbered.photoCount).isEqualTo(0)
    }
}
