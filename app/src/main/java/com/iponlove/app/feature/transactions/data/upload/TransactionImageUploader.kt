package com.iponlove.app.feature.transactions.data.upload

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.PreSyncStep
import com.iponlove.app.feature.transactions.data.local.TransactionImageDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject

/**
 * Pre-sync step that uploads locally-compressed receipt JPEGs to Supabase Storage and stamps
 * the resulting authenticated URL onto the row before [com.iponlove.app.feature.transactions.data.sync.TransactionImageTableSyncer]
 * pushes it to Postgrest. Mirrors [com.iponlove.app.feature.notes.data.upload.NoteAttachmentUploader],
 * keyed on the image id so a transaction can carry several receipts.
 *
 * Storage path: `receipts/{userId}/{transactionId}/{imageId}.jpg` (reuses the existing private
 * `receipts` bucket; the owner RLS still keys on folder[1] = userId).
 *
 * A soft-deleted, never-uploaded row is cleaned up and hard-deleted (it never needs to reach the
 * server). Failures leave the row untouched so the next sync cycle retries.
 */
class TransactionImageUploader @Inject constructor(
    private val dao: TransactionImageDao,
    private val client: SupabaseClient,
    private val currentUser: CurrentUserProvider,
) : PreSyncStep {

    override suspend fun run() {
        val pending = dao.pendingUploads()
        // Session may already be torn down (sign-out race) — skip quietly, retry next sync.
        val userId = runCatching { currentUser.userId() }.getOrNull() ?: return
        for (entity in pending) {
            val file = entity.localPath?.let { File(it) }

            if (entity.isDeleted) {
                file?.delete()
                dao.deleteById(entity.id)
                continue
            }

            if (file == null || !file.exists()) continue

            try {
                val path = objectPath(userId, entity.transactionId, entity.id)
                val bytes = file.readBytes()
                client.storage.from(BUCKET).upload(path, bytes) { upsert = true }
                // Authenticated form: the bucket is private, so the URL is only fetchable with
                // the Supabase token attached (StorageAuthInterceptor) under the bucket's RLS.
                val url = client.storage.from(BUCKET).authenticatedUrl(path)
                dao.markUploaded(entity.id, url)
                file.delete()
            } catch (_: Exception) {
                // Leave for next sync — don't fail the whole sync over one failed upload.
            }
        }
    }

    companion object {
        const val BUCKET = "receipts"

        /** Storage object path: `{userId}/{transactionId}/{imageId}.jpg` (folder[1] = userId, so
         *  the receipts owner RLS is satisfied unchanged). Several receipts per transaction. */
        fun objectPath(userId: String, transactionId: String, imageId: String): String =
            "$userId/$transactionId/$imageId.jpg"
    }
}
