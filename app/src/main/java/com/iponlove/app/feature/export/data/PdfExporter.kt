package com.iponlove.app.feature.export.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.iponlove.app.feature.export.domain.ExportDescription
import com.iponlove.app.feature.export.domain.ReceiptNumbering
import com.iponlove.app.feature.export.domain.model.ExportDateRange
import com.iponlove.app.feature.export.domain.model.ExportRow
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import kotlinx.coroutines.ensureActive
import java.io.File
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Renders an attachment export as a PDF (v1.7.0 Item 6 Slice 2, decision 9).
 *
 * Shape: **page 1+ is the claim sheet** — every transaction in scope as a table row, with only the
 * receipt-bearing ones numbered (`#1`, `#2`, …) — and **pages after it carry one receipt each**, at
 * near-full page size under a caption naming its number. That is the whole linkage: a reader
 * holding a receipt page can find its row, and a reader holding a row knows how many receipt pages
 * to look for. There is deliberately **no signature or approval line** — this is a general export
 * facility, not a form for one employer.
 *
 * Uses `android.graphics.pdf.PdfDocument` from the framework, so the whole feature adds **no
 * dependency**. Photos are fetched, drawn and recycled one at a time (decision 3c); a photo that
 * can't be produced gets a page reading "Receipt unavailable" rather than being dropped
 * (decision 3b).
 */
class PdfExporter @Inject constructor(
    private val fetcher: ReceiptFetcher,
) {

    /**
     * Writes the PDF into [target]. [onPhotoDone] reports 1-based download progress for the sheet's
     * numeric counter (decision 5). Cancellable between photos — the caller deletes [target].
     */
    suspend fun write(
        target: File,
        numbered: ReceiptNumbering.Numbered,
        range: ExportDateRange,
        currency: CurrencySymbol,
        zone: ZoneId,
        onPhotoDone: (done: Int) -> Unit = {},
    ) {
        val doc = PdfDocument()
        try {
            val sheet = ClaimSheet(doc, range, currency, zone)
            sheet.draw(numbered)
            // Page numbering runs continuously across the whole document — the claim sheet may
            // itself have spilled onto several pages, so receipts resume from wherever it ended.
            var pageNumber = sheet.pagesUsed
            for (receipt in numbered.receipts) {
                coroutineContext.ensureActive()
                val bytes = fetcher.fetch(receipt.photo)
                coroutineContext.ensureActive()
                drawReceiptPage(doc, receipt, bytes, currency, zone, ++pageNumber)
                onPhotoDone(receipt.ordinal)
            }
            target.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    // ---- claim sheet ----

    /** Paginated table rendering. Holds the running page/y cursor so rows can spill onto page 2+. */
    private class ClaimSheet(
        private val doc: PdfDocument,
        private val range: ExportDateRange,
        private val currency: CurrencySymbol,
        private val zone: ZoneId,
    ) {
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas = Canvas()
        private var y = 0f

        /** How many pages the claim sheet consumed — where the receipt pages pick up numbering. */
        val pagesUsed: Int get() = pageNumber

        fun draw(numbered: ReceiptNumbering.Numbered) {
            startPage()
            drawHeading(numbered)
            drawColumnHeader()
            var total = BigDecimal.ZERO
            for ((row, marker) in numbered.rows.map { it.row to it.marker }) {
                total = total.add(row.signedAmount)
                if (y + ROW_H > BOTTOM) {
                    finishPage()
                    startPage()
                    drawColumnHeader()
                }
                drawRow(marker, row)
            }
            drawTotal(total)
            finishPage()
        }

        private fun startPage() {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create()
            page = doc.startPage(info)
            canvas = page!!.canvas
            y = MARGIN
        }

        private fun finishPage() {
            page?.let {
                canvas.drawText("Love, Ipon · page $pageNumber", MARGIN, PAGE_H - 22f, footerPaint)
                doc.finishPage(it)
            }
            page = null
        }

        private fun drawHeading(numbered: ReceiptNumbering.Numbered) {
            canvas.drawText("Transactions", MARGIN, y + 18f, titlePaint)
            y += 30f
            canvas.drawText(range.label, MARGIN, y, subtitlePaint)
            y += 14f
            val txns = numbered.rows.size
            val photos = numbered.photoCount
            val summary = "${txns.plural("transaction")} · ${photos.plural("receipt")}"
            canvas.drawText(summary, MARGIN, y, subtitlePaint)
            y += 22f
        }

        private fun drawColumnHeader() {
            canvas.drawText("#", COL_NUM, y, headerPaint)
            canvas.drawText("Date", COL_DATE, y, headerPaint)
            canvas.drawText("Description", COL_DESC, y, headerPaint)
            canvas.drawText("Category", COL_CAT, y, headerPaint)
            canvas.drawText("Account", COL_ACCT, y, headerPaint)
            canvas.drawText("Amount (${currency.glyph})", RIGHT, y, headerAmountPaint)
            y += 6f
            canvas.drawLine(MARGIN, y, RIGHT, y, rulePaint)
            y += ROW_H
        }

        private fun drawRow(marker: Int?, row: ExportRow) {
            marker?.let { canvas.drawText("#$it", COL_NUM, y, bodyBoldPaint) }
            canvas.drawText(DATE_FMT.format(row.date.atZone(zone)), COL_DATE, y, bodyPaint)
            canvas.drawText(ellipsize(ExportDescription.of(row), W_DESC, bodyPaint), COL_DESC, y, bodyPaint)
            canvas.drawText(ellipsize(row.category, W_CAT, bodyPaint), COL_CAT, y, bodyPaint)
            canvas.drawText(ellipsize(row.account, W_ACCT, bodyPaint), COL_ACCT, y, bodyPaint)
            canvas.drawText(AMOUNT_FMT.format(row.signedAmount), RIGHT, y, amountPaint)
            y += ROW_H
        }

        private fun drawTotal(total: BigDecimal) {
            if (y + ROW_H * 2 > BOTTOM) {
                finishPage()
                startPage()
            }
            y += 2f
            canvas.drawLine(MARGIN, y, RIGHT, y, rulePaint)
            y += ROW_H
            canvas.drawText("Total", COL_DESC, y, bodyBoldPaint)
            canvas.drawText(AMOUNT_FMT.format(total), RIGHT, y, amountBoldPaint)
            y += ROW_H
        }
    }

    // ---- receipt pages ----

    private fun drawReceiptPage(
        doc: PdfDocument,
        receipt: ReceiptNumbering.NumberedReceipt,
        bytes: ByteArray?,
        currency: CurrencySymbol,
        zone: ZoneId,
        pageNumber: Int,
    ) {
        val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create()
        val page = doc.startPage(info)
        val canvas = page.canvas
        var y = MARGIN + 14f

        val row = receipt.row
        canvas.drawText(
            ellipsize("${receipt.label}  ${ExportDescription.of(row)}", RIGHT - MARGIN, captionPaint),
            MARGIN,
            y,
            captionPaint,
        )
        y += 16f
        val meta = listOf(
            DATE_FMT.format(row.date.atZone(zone)),
            row.category,
            row.account,
            signedMoney(row.signedAmount, currency),
        ).joinToString("  ·  ")
        canvas.drawText(ellipsize(meta, RIGHT - MARGIN, subtitlePaint), MARGIN, y, subtitlePaint)
        y += 18f

        val areaTop = y
        val areaHeight = BOTTOM - areaTop
        val areaWidth = RIGHT - MARGIN
        val bitmap = bytes?.let { decodeScaled(it) }
        if (bitmap == null) {
            // Decision 3b: never silently skip. An absent photo is stated on its own page so the
            // claim sheet's "#N" always has something to point at.
            canvas.drawText("Receipt unavailable", PAGE_W / 2f, areaTop + areaHeight / 2f, unavailablePaint)
        } else {
            val scale = minOf(areaWidth / bitmap.width, areaHeight / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            val left = MARGIN + (areaWidth - w) / 2f
            val dest = Rect(left.toInt(), areaTop.toInt(), (left + w).toInt(), (areaTop + h).toInt())
            canvas.drawBitmap(bitmap, null, dest, imagePaint)
            bitmap.recycle()
        }
        canvas.drawText("Love, Ipon · page $pageNumber", MARGIN, PAGE_H - 22f, footerPaint)
        doc.finishPage(page)
    }

    /** Decodes at most [MAX_IMAGE_PX] on the long edge — a page is ~515pt wide, so anything larger
     *  is memory spent on detail the PDF cannot show. Returns null on an undecodable payload. */
    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_IMAGE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
    }

    private companion object {
        // A4 at 72dpi, the PostScript-point grid PdfDocument draws on.
        const val PAGE_W = 595
        const val PAGE_H = 842
        const val MARGIN = 40f
        const val RIGHT = PAGE_W - MARGIN
        const val BOTTOM = PAGE_H - MARGIN
        const val ROW_H = 16f
        const val MAX_IMAGE_PX = 1400

        const val COL_NUM = MARGIN
        const val COL_DATE = 68f
        const val COL_DESC = 128f
        const val COL_CAT = 282f
        const val COL_ACCT = 376f
        const val W_DESC = COL_CAT - COL_DESC - 6f
        const val W_CAT = COL_ACCT - COL_CAT - 6f
        const val W_ACCT = 76f

        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
        val AMOUNT_FMT = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

        val titlePaint = paint(16f, Color.BLACK, bold = true)
        val subtitlePaint = paint(9.5f, Color.parseColor("#666666"))
        val headerPaint = paint(9f, Color.parseColor("#444444"), bold = true)
        val headerAmountPaint = paint(9f, Color.parseColor("#444444"), bold = true).apply {
            textAlign = Paint.Align.RIGHT
        }
        val bodyPaint = paint(9f, Color.BLACK)
        val bodyBoldPaint = paint(9f, Color.BLACK, bold = true)
        val amountPaint = paint(9f, Color.BLACK).apply { textAlign = Paint.Align.RIGHT }
        val amountBoldPaint = paint(9f, Color.BLACK, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val captionPaint = paint(12f, Color.BLACK, bold = true)
        val footerPaint = paint(8f, Color.parseColor("#999999"))
        val unavailablePaint = paint(12f, Color.parseColor("#999999")).apply {
            textAlign = Paint.Align.CENTER
        }
        val rulePaint = Paint().apply {
            color = Color.parseColor("#CCCCCC")
            strokeWidth = 0.6f
        }
        val imagePaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }

        fun paint(size: Float, color: Int, bold: Boolean = false) = Paint().apply {
            isAntiAlias = true
            textSize = size
            this.color = color
            typeface = if (bold) {
                android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            } else {
                android.graphics.Typeface.DEFAULT
            }
        }

        /** `-₱2,450.00`, not `₱-2,450.00` — the sign belongs outside the currency glyph. Only the
         *  receipt captions use this; table amounts stay bare so the column still aligns. */
        fun signedMoney(amount: BigDecimal, currency: CurrencySymbol): String {
            val sign = if (amount.signum() < 0) "-" else ""
            return "$sign${currency.glyph}${AMOUNT_FMT.format(amount.abs())}"
        }

        /** Trims [text] to [maxWidth] points, appending an ellipsis when it had to cut. */
        fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
            if (paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
            return text.substring(0, end) + "…"
        }

        fun Int.plural(noun: String): String = "$this $noun${if (this == 1) "" else "s"}"
    }
}
