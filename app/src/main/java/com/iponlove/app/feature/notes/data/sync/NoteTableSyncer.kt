package com.iponlove.app.feature.notes.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.notes.data.local.NoteDao
import com.iponlove.app.feature.notes.data.local.NoteEntity
import com.iponlove.app.feature.notes.data.remote.NoteRemoteSource
import com.iponlove.app.feature.notes.data.toDto
import com.iponlove.app.feature.notes.data.toEntity
import javax.inject.Inject

/**
 * Plugs the notes table into the generic sync engine. Notes are private-to-owner in V1, so
 * the conflict-copy hooks ([isSharedNote]/[conflictCopy]) keep their LWW defaults; the
 * shared-note conflict-copy path (ADR-0003) is wired here when the Couples slice lets a
 * note become shared.
 */
class NoteTableSyncer @Inject constructor(
    private val dao: NoteDao,
    private val remote: NoteRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<NoteEntity>(SyncTable.NOTES, cursors, resolver) {

    override suspend fun dirtyRows(): List<NoteEntity> = dao.dirtyRows()

    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)

    override suspend fun localRow(id: String): NoteEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<NoteEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<NoteEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<NoteEntity>) = dao.applyPullBatch(rows)
}
