package com.iponlove.app.feature.transactions.data.upload

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.PreSyncStep
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.transactions.data.local.TransactionDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject

/**
 * Pre-sync step that uploads locally-compressed receipt JPEGs to Supabase Storage and stamps
 * [TransactionEntity.attachmentUrl] before [TransactionTableSyncer] pushes the row.
 *
 * Storage path: `receipts/{userId}/{transactionId}.jpg`
 *
 * Mirrors [NoteAttachmentUploader]. Failures leave the entity untouched so the next sync
 * retries. A soft-deleted row with a pending local file is cleaned up immediately (the receipt
 * never needs to reach the server).
 */
class ReceiptUploader @Inject constructor(
    private val dao: TransactionDao,
    private val client: SupabaseClient,
    private val currentUser: CurrentUserProvider,
    private val clock: SyncClock,
) : PreSyncStep {

    override suspend fun run() {
        val pending = dao.pendingReceiptUploads()
        // Session may already be torn down (sign-out race) — skip quietly, retry next sync.
        val userId = runCatching { currentUser.userId() }.getOrNull() ?: return
        for (entity in pending) {
            val file = entity.attachmentLocalPath?.let { File(it) }

            if (entity.isDeleted) {
                file?.delete()
                // Row will be pushed as a tombstone by the syncer; just clear the local path
                // by letting the soft-delete flow proceed without an attachmentLocalPath.
                continue
            }

            if (file == null || !file.exists()) continue

            try {
                val path = "$userId/${entity.id}.jpg"
                val bytes = file.readBytes()
                client.storage.from(BUCKET).upload(path, bytes) { upsert = true }
                val url = client.storage.from(BUCKET).publicUrl(path)
                val updatedAt = clock.stamp(entity.updatedAt).toEpochMilli()
                dao.markReceiptUploaded(entity.id, url, updatedAt)
                file.delete()
            } catch (_: Exception) {
                // Leave for next sync — don't fail the whole sync over one failed upload.
            }
        }
    }

    companion object {
        const val BUCKET = "receipts"
    }
}
