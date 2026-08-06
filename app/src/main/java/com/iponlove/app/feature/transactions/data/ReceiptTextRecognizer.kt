package com.iponlove.app.feature.transactions.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.iponlove.app.feature.transactions.domain.model.RecognizedLine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The ML Kit boundary (v1.7.3 Item 2, ADR-0062 decision 1): bundled Text Recognition v2, Latin
 * script, on-device, no network. Converts an image into the plain [RecognizedLine] list
 * [ReceiptParser][com.iponlove.app.feature.transactions.domain.usecase.ReceiptParser] parses, so
 * every parsing decision stays JVM-testable without mocking ML Kit at all.
 *
 * **[InputImage.fromFilePath] is load-bearing, not incidental** (decision 10): it reads and
 * applies EXIF orientation itself. Decoding to a bitmap first and calling `fromBitmap(bitmap, 0)`
 * is the silent-garbage path — a rotated capture would recognise as noise with no error.
 *
 * The recognizer is held for the app's lifetime rather than created per scan: the bundled model
 * initialises on first use, and a retake is a likely immediate second call.
 */
@Singleton
class ReceiptTextRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Recognised lines in reading order (top-to-bottom, then left-to-right) — the order the
     * parser's "later line wins" tie-break and its last-number fallback both depend on. ML Kit
     * returns blocks in no guaranteed order, so the sort here is not cosmetic.
     *
     * Returns an empty list when nothing was recognised, which is the caller's "Couldn't read
     * that one" signal (decision 8).
     */
    suspend fun recognize(uri: Uri): List<RecognizedLine> = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val text = suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
        text.textBlocks
            .flatMap { block -> block.lines }
            .mapIndexed { index, line -> index to line }
            .sortedWith(
                compareBy(
                    { (_, line) -> line.boundingBox?.top ?: 0 },
                    { (_, line) -> line.boundingBox?.left ?: 0 },
                    { (index, _) -> index },
                ),
            )
            .map { (_, line) ->
                val box = line.boundingBox
                RecognizedLine(
                    text = line.text,
                    top = box?.top ?: 0,
                    height = box?.height() ?: 0,
                    left = box?.left ?: 0,
                )
            }
    }
}
