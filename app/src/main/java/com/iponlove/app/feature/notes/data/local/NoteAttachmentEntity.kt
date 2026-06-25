package com.iponlove.app.feature.notes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import java.time.Instant

/**
 * Room mirror of a `note_images` row plus local-only upload state.
 *
 * [type] defaults to "IMAGE" in V1; the column exists so voice recordings can be
 * added as a new type post-V1 without a schema migration (CLAUDE.md scalability note).
 *
 * [localPath] holds the compressed JPEG path while the file is awaiting upload.
 * [NoteAttachmentUploader] sets it to null and stamps [url] once the upload succeeds.
 * After that, [url] is the authoritative Supabase Storage URL.
 *
 * [pendingSync] remains true until the row is pushed to Postgrest (after upload).
 * [NoteAttachmentTableSyncer.dirtyRows] only returns rows where [url] IS NOT NULL so
 * pre-upload rows are never pushed.
 */
@Entity(tableName = "note_attachments")
data class NoteAttachmentEntity(
    @PrimaryKey override val id: String,
    val noteId: String,
    val type: String,
    val localPath: String?,
    val url: String?,
    val position: Int,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
