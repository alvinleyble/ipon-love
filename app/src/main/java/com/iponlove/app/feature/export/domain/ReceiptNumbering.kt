package com.iponlove.app.feature.export.domain

import com.iponlove.app.feature.export.domain.model.ExportPhoto
import com.iponlove.app.feature.export.domain.model.ExportRow

/**
 * Assigns the claim-sheet numbers that tie a PDF/ZIP's transaction rows to their receipt photos
 * (v1.7.0 Item 6 decision 9). Pure — the whole row↔photo linkage is decided here and unit-tested,
 * leaving [com.iponlove.app.feature.export.data.PdfExporter] and
 * [com.iponlove.app.feature.export.data.ZipExporter] to do nothing but draw/stream what it says.
 *
 * Rules: **only receipt-bearing rows are numbered**, in row order (`#1`, `#2`, …) — a row with no
 * photo gets no marker and no number is burned on it. A row with a single photo labels it plainly
 * (`#3`); a row with several suffixes them (`#3a`, `#3b`, `#3c`), so a reader who sees `#3b` on a
 * receipt page can find row `#3` on the claim sheet and know it is the second of that row's photos.
 */
object ReceiptNumbering {

    /** One export row paired with its claim-sheet [marker], or `null` when it carries no receipt. */
    data class NumberedRow(val row: ExportRow, val marker: Int?)

    /**
     * One receipt photo, located: [marker] is its row's claim number, [suffix] disambiguates
     * multiple photos on that row (`""` when it is the row's only one), [label] is what gets
     * printed (`#3` / `#3b`), and [ordinal] is its 1-based position across the whole export —
     * the download-progress counter and the ZIP's sort key.
     */
    data class NumberedReceipt(
        val marker: Int,
        val suffix: String,
        val ordinal: Int,
        val photo: ExportPhoto,
        val row: ExportRow,
    ) {
        val label: String get() = "#$marker$suffix"
    }

    /** The fully-numbered export: rows in their original order, plus every photo flattened out. */
    data class Numbered(
        val rows: List<NumberedRow>,
        val receipts: List<NumberedReceipt>,
    ) {
        val photoCount: Int get() = receipts.size

        /** Photos that would have to be downloaded — the offline block's input (decision 3a). */
        val downloadCount: Int get() = receipts.count { it.photo.needsDownload }
    }

    fun of(rows: List<ExportRow>): Numbered {
        val numberedRows = mutableListOf<NumberedRow>()
        val receipts = mutableListOf<NumberedReceipt>()
        var marker = 0
        for (row in rows) {
            if (row.receipts.isEmpty()) {
                numberedRows += NumberedRow(row, marker = null)
                continue
            }
            marker++
            numberedRows += NumberedRow(row, marker = marker)
            val multiple = row.receipts.size > 1
            row.receipts.forEachIndexed { index, photo ->
                receipts += NumberedReceipt(
                    marker = marker,
                    suffix = if (multiple) ('a' + index).toString() else "",
                    ordinal = receipts.size + 1,
                    photo = photo,
                    row = row,
                )
            }
        }
        return Numbered(numberedRows, receipts)
    }
}
