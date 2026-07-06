package com.iponlove.app.feature.transactions.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import java.time.Instant

/**
 * Room mirror of a `transaction_images` row plus local-only upload state — a receipt photo
 * for a transaction. Mirrors [com.iponlove.app.feature.notes.data.local.NoteAttachmentEntity].
 *
 * [localPath] holds the compressed JPEG path while the file is awaiting upload;
 * [com.iponlove.app.feature.transactions.data.upload.TransactionImageUploader] sets it to null
 * and stamps [url] once the upload succeeds. After that [url] is the authoritative Storage URL.
 *
 * [pendingSync] stays true until the row is pushed to Postgrest (after upload). The syncer's
 * dirty-row query only returns rows where [url] IS NOT NULL, so pre-upload rows are never pushed.
 */
@Entity(
    tableName = "transaction_images",
    indices = [Index("transactionId")],
)
data class TransactionImageEntity(
    @PrimaryKey override val id: String,
    val transactionId: String,
    val localPath: String?,
    val url: String?,
    val position: Int,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
