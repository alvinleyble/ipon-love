package com.iponlove.app.feature.notes.data.upload

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.PreSyncStep
import com.iponlove.app.feature.notes.data.local.NoteAttachmentDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject

/**
 * Pre-sync step that uploads locally-compressed JPEGs to Supabase Storage and stamps the
 * resulting public URL onto the Row before [NoteAttachmentTableSyncer] pushes it to Postgrest.
 *
 * Storage path: `note-images/{userId}/{noteId}/{id}.jpg`
 *
 * If an attachment is already deleted and hasn't been uploaded yet, the local file is cleaned
 * up and the row is hard-deleted (it never needs to reach the server). Failures leave the row
 * untouched so the next sync cycle retries.
 */
class NoteAttachmentUploader @Inject constructor(
    private val dao: NoteAttachmentDao,
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

            if (file == null || !file.exists()) {
                // File missing — skip this row; it will sit with localPath until cleaned up
                continue
            }

            try {
                val path = "$userId/${entity.noteId}/${entity.id}.jpg"
                val bytes = file.readBytes()
                client.storage.from(BUCKET).upload(path, bytes) { upsert = true }
                val url = client.storage.from(BUCKET).publicUrl(path)
                dao.markUploaded(entity.id, url)
                file.delete()
            } catch (_: Exception) {
                // Leave for next sync — don't fail the whole sync over one failed upload
            }
        }
    }

    companion object {
        const val BUCKET = "note-images"
    }
}
