package com.iponlove.app.feature.couple.data.upload

import android.graphics.Bitmap
import com.iponlove.app.BuildConfig
import com.iponlove.app.core.network.StorageUrlRewrite
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

/**
 * Uploads (and deletes) the couple's shared banner photo in the private `couple-banners` bucket
 * (v1.7.0 Item 10). Unlike [com.iponlove.app.feature.transactions.data.upload.TransactionImageUploader]
 * (a deferred pre-sync step keyed on a Room row), this is an **interactive, online-only** uploader
 * driven by [com.iponlove.app.feature.couple.domain.usecase.SetCoupleBannerUseCase] — pairing-style,
 * like [com.iponlove.app.feature.couple.data.CoupleRepositoryImpl]'s RPCs.
 *
 * Storage path: `couple-banners/{couple_id}/{randomUUID}.jpg`. `folder[1] = couple_id` is the RLS
 * key (mirroring `receipts`' `folder[1] = userId`), and a **fresh random filename each upload** means
 * a replaced banner gets a new [authenticatedUrl], so Coil never serves a stale cached image.
 */
class CoupleBannerUploader @Inject constructor(
    private val client: SupabaseClient,
) {
    private val supabaseUrl: String get() = BuildConfig.SUPABASE_URL

    /** Compress [bitmap] (≤1440px, JPEG 85%) and upload it under [coupleId]; returns its
     *  authenticated URL. Throws on failure so the caller leaves the old banner in place. */
    suspend fun upload(coupleId: String, bitmap: Bitmap): String {
        val bytes = compress(bitmap)
        val path = "$coupleId/${UUID.randomUUID()}.jpg"
        client.storage.from(BUCKET).upload(path, bytes) { upsert = true }
        // Authenticated form: private bucket, so the URL only resolves with the token attached
        // (StorageAuthInterceptor) under the couple-banners RLS.
        return client.storage.from(BUCKET).authenticatedUrl(path)
    }

    /** Best-effort delete of the previous banner object after a replace/remove (decision 6). A
     *  stale orphan is unreadable post-unpair anyway, so a failure here is hygiene, not a leak. */
    suspend fun deleteObject(url: String) {
        val ref = StorageUrlRewrite.toObjectRef(url, supabaseUrl) ?: return
        if (ref.bucket != BUCKET) return
        runCatching { client.storage.from(BUCKET).delete(ref.path) }
    }

    private fun compress(bitmap: Bitmap): ByteArray {
        val scaled = scaledDown(bitmap)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (scaled !== bitmap) scaled.recycle()
        return out.toByteArray()
    }

    private fun scaledDown(src: Bitmap): Bitmap {
        val max = 1440
        if (src.width <= max && src.height <= max) return src
        val ratio = max.toFloat() / maxOf(src.width, src.height)
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt(),
            (src.height * ratio).toInt(),
            true,
        )
    }

    companion object {
        const val BUCKET = "couple-banners"
    }
}
