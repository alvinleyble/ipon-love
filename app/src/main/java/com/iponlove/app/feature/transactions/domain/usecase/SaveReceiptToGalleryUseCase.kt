package com.iponlove.app.feature.transactions.domain.usecase

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Writes the scanned receipt into a dedicated **`Pictures/Love, Ipon`** album (ADR-0062 decision
 * 7) — never the camera-roll root. Free for everyone and never gated: it costs no Storage egress,
 * no sync and no cap, and it is the user's own photo on their own phone.
 *
 * Three rules from the decision are enforced by the callers, not here, and are worth naming:
 * this runs **on Save, never at capture** (writing at capture would pollute the gallery with
 * abandoned scans, outside every sweep the app owns and possibly already synced to Google Photos);
 * it is **camera-leg only** (a gallery-picked image is already in the gallery); and its source is
 * the **full-resolution** `cacheDir/scans` temp file, not the 1080px/JPEG-85 storage copy —
 * handing back a downgraded re-encode would be a silent quality loss against the decision's own
 * framing.
 *
 * Never throws: a gallery-copy failure must not fail the transaction save that triggered it.
 */
class SaveReceiptToGalleryUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** @return true when a copy landed in the album. */
    suspend operator fun invoke(sourcePath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(sourcePath)
            if (!source.exists()) return@runCatching false
            val name = "LoveIpon-${TIMESTAMP.format(LocalDateTime.now())}.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(source, name)
            } else {
                writeLegacy(source, name)
            }
        }.getOrDefault(false)
    }

    /** API 29+: no permission needed; `IS_PENDING` keeps a half-written file out of the gallery. */
    private fun writeViaMediaStore(source: File, name: String): Boolean {
        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pending)
            ?: return false
        val wrote = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } != null
        }.getOrDefault(false)
        if (!wrote) {
            resolver.delete(uri, null, null)
            return false
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return true
    }

    /**
     * API 26–28: a plain file write plus a media scan, which is why `WRITE_EXTERNAL_STORAGE` is
     * declared with `maxSdkVersion="28"`. Silently skipped rather than thrown when the permission
     * was declined — the gallery copy is a convenience, and Save must still complete.
     */
    @Suppress("DEPRECATION")
    private fun writeLegacy(source: File, name: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        ).also { it.mkdirs() }
        val destination = File(dir, name)
        source.copyTo(destination, overwrite = true)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destination.absolutePath),
            arrayOf(MIME_TYPE),
            null,
        )
        return true
    }

    private companion object {
        const val ALBUM = "Love, Ipon"
        const val MIME_TYPE = "image/jpeg"
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    }
}
