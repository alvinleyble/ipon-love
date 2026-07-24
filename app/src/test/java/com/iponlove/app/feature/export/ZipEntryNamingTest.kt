package com.iponlove.app.feature.export

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.export.domain.ReceiptNumbering
import com.iponlove.app.feature.export.domain.ZipEntryNaming
import com.iponlove.app.feature.export.domain.model.ExportPhoto
import com.iponlove.app.feature.export.domain.model.ExportRow
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * ZIP entry naming (v1.7.0 Item 6 Slice 2, decision 9). The requirement being tested is
 * "identifiable **after extraction**" — once a photo has been dragged out of the archive it has
 * nothing but its filename left, so the claim number, date and description all have to survive in
 * it, and it has to be safe on any filesystem it lands on.
 */
class ZipEntryNamingTest {

    private val zone = ZoneOffset.UTC

    private fun receipt(
        marker: Int,
        suffix: String = "",
        note: String = "Grab to client",
        category: String = "Transportation",
        date: String = "2026-07-15T02:00:00Z",
    ): ReceiptNumbering.NumberedReceipt {
        val row = ExportRow(
            date = Instant.parse(date),
            type = TransactionType.EXPENSE,
            category = category,
            account = "Cash",
            note = note,
            signedAmount = BigDecimal("-1200.00"),
            receipts = listOf(ExportPhoto(id = "img", url = "https://example/img.jpg")),
        )
        return ReceiptNumbering.NumberedReceipt(marker, suffix, 1, row.receipts.first(), row)
    }

    @Test
    fun `a photo entry carries number, date and description under receipts slash`() {
        assertThat(ZipEntryNaming.photoEntry(receipt(3, "b"), zone))
            .isEqualTo("receipts/03b_2026-07-15_Grab-to-client.jpg")
    }

    @Test
    fun `the claim number is zero-padded so extracted folders sort in claim order`() {
        val third = ZipEntryNaming.photoEntry(receipt(3), zone)
        val tenth = ZipEntryNaming.photoEntry(receipt(10), zone)
        assertThat(third).contains("/03_")
        assertThat(tenth).contains("/10_")
        assertThat(listOf(tenth, third).sorted()).containsExactly(third, tenth).inOrder()
    }

    @Test
    fun `description falls back to the category when there is no note`() {
        assertThat(ZipEntryNaming.photoEntry(receipt(1, note = ""), zone))
            .isEqualTo("receipts/01_2026-07-15_Transportation.jpg")
    }

    @Test
    fun `filesystem-unsafe characters are slugged away`() {
        val name = ZipEntryNaming.photoEntry(receipt(1, note = "Lunch: client / \"big\" deal*?"), zone)
        assertThat(name).isEqualTo("receipts/01_2026-07-15_Lunch-client-big-deal.jpg")
        assertThat(name.substringAfterLast('/')).doesNotContain("/")
    }

    @Test
    fun `a very long note is truncated without a trailing hyphen`() {
        val name = ZipEntryNaming.photoEntry(receipt(1, note = "a ".repeat(60)), zone)
        val stem = name.removePrefix("receipts/").removeSuffix(".jpg")
        assertThat(stem).doesNotContain("--")
        assertThat(stem.last()).isNotEqualTo('-')
        assertThat(stem.length).isAtMost("01_2026-07-15_".length + 40)
    }

    @Test
    fun `a description that slugs to nothing still yields a valid name`() {
        assertThat(ZipEntryNaming.photoEntry(receipt(2, note = "***", category = ""), zone))
            .isEqualTo("receipts/02_2026-07-15.jpg")
    }

    @Test
    fun `an unavailable photo keeps the same stem with a txt marker`() {
        val r = receipt(3, "a")
        assertThat(ZipEntryNaming.unavailableEntry(r, zone))
            .isEqualTo("receipts/03a_2026-07-15_Grab-to-client_UNAVAILABLE.txt")
        // Same stem as the photo would have had, so the gap is obvious in a sorted listing.
        assertThat(ZipEntryNaming.unavailableEntry(r, zone).substringBefore("_UNAVAILABLE"))
            .isEqualTo(ZipEntryNaming.photoEntry(r, zone).removeSuffix(".jpg"))
    }

    @Test
    fun `the csv entry name is fixed`() {
        assertThat(ZipEntryNaming.CSV_ENTRY).isEqualTo("transactions.csv")
    }
}
