package com.iponlove.app.feature.transactions.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Owns the full-resolution capture temp file between [TakePicture][androidx.activity.result.contract.ActivityResultContracts.TakePicture]
 * and the recognise-then-compress step, at `cacheDir/scans/{uuid}.jpg` (ADR-0062 decision 9).
 * Modelled on [com.iponlove.app.feature.export.data.ExportFileWriter], with one deliberate
 * deviation: [sweep] is age-based, not unconditional. `ACTION_IMAGE_CAPTURE` hands off to a
 * separate camera process, and on a low-RAM device this app's process is routinely killed while
 * the camera is foreground; `ActivityResultRegistry` redelivers the pending result across that
 * restart, so an unconditional sweep at [IponApp.onCreate][com.iponlove.app.IponApp.onCreate]
 * would delete the in-flight capture before the redelivered result is read. An in-flight capture
 * is seconds-to-minutes old; an abandoned one is found at the next cold start after that.
 */
class ReceiptScanFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** A fresh `cacheDir/scans/{uuid}.jpg`, deleting any stale file of the same (impossible, but
     *  mirrors [com.iponlove.app.feature.export.data.ExportFileWriter.newFile]'s delete-if-exists
     *  discipline) name first, plus its shareable [FileProvider] content Uri. */
    fun newCapture(): ReceiptCapture {
        val dir = File(context.cacheDir, DIR).also { it.mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg").also { if (it.exists()) it.delete() }
        return ReceiptCapture(file, uriFor(file))
    }

    /** The shareable content Uri for a file produced by [newCapture]. */
    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Deletes [path] if it still exists — called after recognise+compress succeeds (unless the
     *  gallery-copy toggle is holding it for Save), and on retake before the next capture. */
    fun delete(path: String) {
        File(path).delete()
    }

    /** The startup sweep wired into `IponApp.onCreate` — the backstop for process death and hard
     *  kills. Deletes only files older than [MAX_AGE_MS], never the whole directory. */
    fun sweep(now: Long = System.currentTimeMillis()) {
        val dir = File(context.cacheDir, DIR)
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (isExpired(file.lastModified(), now)) file.delete()
        }
    }

    companion object {
        private const val DIR = "scans"
        const val MAX_AGE_MS = 60 * 60 * 1000L // one hour

        /** Pure age predicate behind [sweep], kept separate so it's JVM-unit-testable without a
         *  real filesystem or [Context]. */
        fun isExpired(lastModifiedMs: Long, nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean =
            nowMs - lastModifiedMs > maxAgeMs
    }
}

/** A freshly minted capture target: the file the camera writes into, and its content Uri. */
data class ReceiptCapture(val file: File, val uri: Uri)
