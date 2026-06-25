package com.iponlove.app.feature.notes.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.notes.data.local.NoteDao
import com.iponlove.app.feature.notes.data.local.NoteEntity
import com.iponlove.app.feature.notes.data.remote.NoteRemoteSource
import com.iponlove.app.feature.notes.data.toDto
import com.iponlove.app.feature.notes.data.toEntity
import java.util.UUID
import javax.inject.Inject

/**
 * Plugs the notes table into the generic sync engine. Wires the shared-note conflict-copy
 * safeguard (ADR-0003): when the server has a newer version of a note the user is currently
 * editing, the local edits are forked into a new note prefixed "[Conflict Copy]" rather than
 * silently discarded.
 */
class NoteTableSyncer @Inject constructor(
    private val dao: NoteDao,
    private val remote: NoteRemoteSource,
    private val clock: SyncClock,
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

    override fun isSharedNote(row: NoteEntity): Boolean = row.isShared

    override suspend fun conflictCopy(local: NoteEntity) {
        val now = clock.stamp(null)
        dao.upsert(
            NoteEntity(
                id = UUID.randomUUID().toString(),
                userId = local.userId,
                title = "[Conflict Copy] ${local.title.orEmpty()}",
                content = local.content,
                isShared = false,
                coupleId = null,
                isConflictCopy = true,
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                serverRev = null,
                pendingSync = true,
            ),
        )
    }
}
