package com.iponlove.app.feature.notes.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.notes.data.local.NoteAttachmentDao
import com.iponlove.app.feature.notes.data.local.NoteAttachmentEntity
import com.iponlove.app.feature.notes.data.remote.NoteAttachmentRemoteSource
import com.iponlove.app.feature.notes.data.toDto
import com.iponlove.app.feature.notes.data.toEntity
import javax.inject.Inject

/**
 * Syncs the `note_images` table. [dirtyRows] intentionally filters to uploaded-only rows
 * (`url IS NOT NULL`) — pre-upload rows are handled by [NoteAttachmentUploader] which runs
 * before the standard push/pull loop in [SyncEngine].
 */
class NoteAttachmentTableSyncer @Inject constructor(
    private val dao: NoteAttachmentDao,
    private val remote: NoteAttachmentRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<NoteAttachmentEntity>(SyncTable.NOTE_IMAGES, cursors, resolver) {

    override suspend fun dirtyRows(): List<NoteAttachmentEntity> = dao.dirtyRows()

    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)

    override suspend fun localRow(id: String): NoteAttachmentEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<NoteAttachmentEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<NoteAttachmentEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<NoteAttachmentEntity>) =
        dao.applyPullBatch(rows)
}
