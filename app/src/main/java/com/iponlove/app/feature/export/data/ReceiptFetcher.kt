package com.iponlove.app.feature.export.data

import com.iponlove.app.BuildConfig
import com.iponlove.app.core.network.StorageUrlRewrite
import com.iponlove.app.feature.export.domain.model.ExportPhoto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject

/**
 * Resolves one receipt photo to bytes for an attachment export (v1.7.0 Item 6 decision 3):
 * **use the local file if it is still there, otherwise download it.**
 *
 * The asymmetry is the point. [com.iponlove.app.feature.transactions.data.upload.TransactionImageUploader]
 * deletes the compressed JPEG the moment it uploads, keeping only a private-bucket URL — so most
 * receipts genuinely are not on the phone and an attachment export is the one place this
 * offline-first app must reach the network. A photo picked but not yet synced is the exception, and
 * costs nothing to serve locally.
 *
 * Returns `null` when the photo cannot be produced at all. Callers must surface that as
 * "Receipt unavailable" rather than skipping the receipt silently (decision 3b) — a claim sheet
 * that quietly drops a line is worse than one that admits a gap.
 *
 * One photo at a time, bytes never retained (decision 3c): a 100-photo export must never hold 100
 * decoded images at once.
 */
class ReceiptFetcher @Inject constructor(
    private val client: SupabaseClient,
) {
    // Not a constructor default — Dagger ignores those, and the flavor's URL is a build constant
    // rather than an injected dependency. The pure URL→(bucket, path) step it feeds is unit-tested
    // on its own in StorageUrlRewriteTest.
    private val supabaseUrl: String get() = BuildConfig.SUPABASE_URL

    suspend fun fetch(photo: ExportPhoto): ByteArray? {
        photo.localPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                runCatching { file.readBytes() }.getOrNull()?.let { return it }
            }
            // Fall through: the row still claims a local file but it is gone (Item 14's sweep, a
            // cache wipe). A URL, if the row has one, is the better answer than giving up.
        }
        val url = photo.url ?: return null
        val ref = StorageUrlRewrite.toObjectRef(url, supabaseUrl) ?: return null
        return runCatching {
            client.storage.from(ref.bucket).downloadAuthenticated(ref.path)
        }.getOrNull()
    }
}
