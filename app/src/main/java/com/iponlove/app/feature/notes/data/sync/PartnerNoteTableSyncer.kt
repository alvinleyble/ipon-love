package com.iponlove.app.feature.notes.data.sync

import com.iponlove.app.core.sync.BasePartnerTableSyncer
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.notes.data.local.NoteDao
import com.iponlove.app.feature.notes.data.local.NoteEntity
import com.iponlove.app.feature.notes.data.remote.NoteRemoteSource
import com.iponlove.app.feature.notes.data.toEntity
import javax.inject.Inject

/**
 * Replicates the partner's shared notes from the `partner_notes` view into the shared
 * `notes` Room table (ADR-0004/0005). An unshared OR deleted note crosses with content
 * nulled → purged locally; a shared, non-deleted note is upserted. (The shared-note
 * conflict-copy path is owner-side, on the base-table syncer — partner notes are read-only.)
 */
class PartnerNoteTableSyncer @Inject constructor(
    private val dao: NoteDao,
    private val remote: NoteRemoteSource,
    cursors: SyncCursorStore,
) : BasePartnerTableSyncer<NoteEntity>(SyncTable.PARTNER_NOTES, cursors) {

    override suspend fun remotePullPartner(cursor: Long, limit: Int): List<NoteEntity> =
        remote.pullPartner(cursor, limit).map { it.toEntity() }

    override fun shouldPurge(row: NoteEntity): Boolean = !row.isShared || row.isDeleted
    override suspend fun hardDelete(id: String) = dao.deleteById(id)
    override suspend fun applyPullBatch(rows: List<NoteEntity>) = dao.applyPullBatch(rows)
}
