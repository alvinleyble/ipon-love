package com.iponlove.app.feature.export.domain

import com.iponlove.app.feature.export.domain.ReceiptNumbering.NumberedReceipt
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Names the entries inside an attachment ZIP (v1.7.0 Item 6 decision 9). The whole point of the
 * scheme is that a photo stays identifiable **after extraction**, when it has been dragged out of
 * the archive and lost all context: `receipts/03b_2026-07-15_Grab-to-client.jpg` carries its claim
 * number (matching the bundled `transactions.csv`), its date, and its description.
 *
 * The claim number is zero-padded to two digits so an extracted folder sorts in claim order rather
 * than lexicographically (`03` before `10`), and the description is slugged to stay safe on every
 * filesystem a shared archive might land on.
 */
object ZipEntryNaming {

    const val CSV_ENTRY = "transactions.csv"
    private const val DIR = "receipts/"
    private const val MAX_SLUG = 40

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    /** `receipts/03b_2026-07-15_Grab-to-client.jpg` — the photo itself. */
    fun photoEntry(receipt: NumberedReceipt, zone: ZoneId): String =
        DIR + stem(receipt, zone) + ".jpg"

    /**
     * `receipts/03b_2026-07-15_Grab-to-client_UNAVAILABLE.txt` — stands in for a photo that could
     * not be fetched. A ZIP can't "print" a message the way a PDF page can (decision 3b), so the
     * archive carries a same-named placeholder instead: the receipt is still visibly accounted for
     * after extraction rather than silently missing from the folder.
     */
    fun unavailableEntry(receipt: NumberedReceipt, zone: ZoneId): String =
        DIR + stem(receipt, zone) + "_UNAVAILABLE.txt"

    private fun stem(receipt: NumberedReceipt, zone: ZoneId): String {
        val number = "%02d%s".format(Locale.US, receipt.marker, receipt.suffix)
        val date = DATE.format(receipt.row.date.atZone(zone))
        val slug = slug(ExportDescription.of(receipt.row))
        return if (slug.isEmpty()) "${number}_$date" else "${number}_${date}_$slug"
    }

    /** Filesystem-safe, hyphen-joined, length-capped — never leading/trailing hyphens. */
    private fun slug(text: String): String = text
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .joinToString("-")
        .take(MAX_SLUG)
        .trim('-')
}
