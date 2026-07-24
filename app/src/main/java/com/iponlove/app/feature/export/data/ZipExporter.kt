package com.iponlove.app.feature.export.data

import com.iponlove.app.feature.export.domain.CsvExporter
import com.iponlove.app.feature.export.domain.ReceiptNumbering
import com.iponlove.app.feature.export.domain.ZipEntryNaming
import kotlinx.coroutines.ensureActive
import java.io.File
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Bundles an attachment export as a ZIP (v1.7.0 Item 6 Slice 2, decision 9): the same
 * `transactions.csv` a free CSV export produces, plus a `receipts/` folder whose filenames stay
 * meaningful once extracted (`receipts/03b_2026-07-15_Grab-to-client.jpg`) — the ZIP's answer to
 * the PDF's claim-sheet numbering, for anyone who wants the raw files rather than a document.
 *
 * `java.util.zip` is JVM-stock, so like the PDF this adds **no dependency**. Photos are streamed
 * straight from the fetcher into the archive one at a time and never accumulated (decision 3c), and
 * a photo that can't be produced becomes a same-named `_UNAVAILABLE.txt` marker instead of vanishing
 * from the folder (decision 3b).
 */
class ZipExporter @Inject constructor(
    private val fetcher: ReceiptFetcher,
) {

    /**
     * Writes the archive into [target]. [onPhotoDone] reports 1-based download progress for the
     * sheet's numeric counter (decision 5). Cancellable between photos — the caller deletes [target].
     */
    suspend fun write(
        target: File,
        numbered: ReceiptNumbering.Numbered,
        zone: ZoneId,
        onPhotoDone: (done: Int) -> Unit = {},
    ) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(ZipEntryNaming.CSV_ENTRY))
            zip.write((CsvExporter.BOM + CsvExporter.build(numbered.rows.map { it.row }, zone)).toByteArray())
            zip.closeEntry()

            for (receipt in numbered.receipts) {
                coroutineContext.ensureActive()
                val bytes = fetcher.fetch(receipt.photo)
                coroutineContext.ensureActive()
                if (bytes == null) {
                    zip.putNextEntry(ZipEntry(ZipEntryNaming.unavailableEntry(receipt, zone)))
                    zip.write(UNAVAILABLE_NOTE.toByteArray())
                } else {
                    zip.putNextEntry(ZipEntry(ZipEntryNaming.photoEntry(receipt, zone)))
                    zip.write(bytes)
                }
                zip.closeEntry()
                onPhotoDone(receipt.ordinal)
            }
        }
    }

    private companion object {
        const val UNAVAILABLE_NOTE =
            "Receipt unavailable — this photo could not be downloaded when the export was created."
    }
}
