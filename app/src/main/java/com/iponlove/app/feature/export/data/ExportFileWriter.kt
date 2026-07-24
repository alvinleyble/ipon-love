package com.iponlove.app.feature.export.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Writes an export to a *temporary* file in `cacheDir/exports` and hands back a [FileProvider]
 * content Uri for the share sheet (v1.7.0 Item 6, decision 7). The file is a transmission, not a
 * stored document — the OS may clear the cache freely, and [sweep] (run on app start) clears any
 * leftovers, so an export never lingers (deliberately avoiding Item 14's orphaned-file leak).
 */
class ExportFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Writes UTF-8 [content] to `exports/[filename]`, replacing any existing file, and returns its
     *  shareable content Uri. */
    fun write(filename: String, content: String): Uri = uriFor(newFile(filename).also { it.writeText(content) })

    /**
     * An empty `exports/[filename]` ready to be *streamed* into, replacing any existing file. The
     * attachment formats (Slice 2) can't build their payload as a String first — a PDF or ZIP is
     * written incrementally, one receipt at a time, precisely so a 100-photo export never sits in
     * memory whole (decision 3c).
     */
    fun newFile(filename: String): File {
        val dir = File(context.cacheDir, DIR).also { it.mkdirs() }
        return File(dir, filename).also { if (it.exists()) it.delete() }
    }

    /** The shareable content Uri for a file produced by [newFile]. */
    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Deletes every file in `cacheDir/exports` — the startup sweep wired into `IponApp`. */
    fun sweep() {
        File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val DIR = "exports"
    }
}
