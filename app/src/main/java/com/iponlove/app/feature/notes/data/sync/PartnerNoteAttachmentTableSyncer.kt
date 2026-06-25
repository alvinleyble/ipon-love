package com.iponlove.app.feature.notes.data.sync

import com.iponlove.app.core.sync.BasePartnerTableSyncer
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.notes.data.local.NoteAttachmentDao
import com.iponlove.app.feature.notes.data.local.NoteAttachmentEntity
import com.iponlove.app.feature.notes.data.remote.NoteAttachmentRemoteSource
import com.iponlove.app.feature.notes.data.toEntity
import javax.inject.Inject

/**
 * Replicates the partner's note images from the `partner_note_images` view (ADR-0004/0005).
 * A row is purged when [storageUrl] is null (the parent note became private/deleted/unshared)
 * or the row is deleted; otherwise the image URL is upserted for display.
 */
class PartnerNoteAttachmentTableSyncer @Inject constructor(
    private val dao: NoteAttachmentDao,
    private val remote: NoteAttachmentRemoteSource,
    cursors: SyncCursorStore,
) : BasePartnerTableSyncer<NoteAttachmentEntity>(SyncTable.PARTNER_NOTE_IMAGES, cursors) {

    override suspend fun remotePullPartner(cursor: Long, limit: Int): List<NoteAttachmentEntity> =
        remote.pullPartner(cursor, limit).map { it.toEntity() }

    override fun shouldPurge(row: NoteAttachmentEntity): Boolean =
        row.url == null || row.isDeleted

    override suspend fun hardDelete(id: String) = dao.deleteById(id)

    override suspend fun applyPullBatch(rows: List<NoteAttachmentEntity>) =
        dao.applyPullBatch(rows)
}
