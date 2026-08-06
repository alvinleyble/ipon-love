package com.iponlove.app.feature.transactions.domain.usecase

import android.net.Uri
import com.iponlove.app.feature.transactions.data.ReceiptTextRecognizer
import com.iponlove.app.feature.transactions.domain.model.ReceiptScanResult
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Recognise a receipt image, then parse it into a prefilled draft (v1.7.3 Item 2, ADR-0062).
 *
 * Runs on the **full-resolution** image, upstream of [CompressReceiptUseCase]'s 1080px/JPEG-85
 * downscale (ADR-0062 finding 5) — that storage size sits below what reliably reads small thermal
 * print, so the order is capture → recognise → parse → *then* compress.
 *
 * Returns null when nothing at all was recognised — the "Couldn't read that one" case (decision 8),
 * distinct from a [ReceiptScanResult] that merely came back partly empty, which is not a failure.
 */
class ScanReceiptUseCase @Inject constructor(
    private val recognizer: ReceiptTextRecognizer,
) {
    suspend operator fun invoke(uri: Uri): ReceiptScanResult? {
        val lines = recognizer.recognize(uri)
        if (lines.isEmpty()) return null
        return ReceiptParser.parse(lines, LocalDate.now(PH_ZONE))
    }

    private companion object {
        // The date bound is a PH-calendar judgement ("not in the future, not older than 18
        // months"), so it keys on PH-local today — not the device zone. Same reasoning as the
        // frozen cross-platform contract §4's monthly aggregates.
        val PH_ZONE: ZoneId = ZoneId.of("Asia/Manila")
    }
}
