package com.iponlove.app.feature.notes.data

import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.notes.data.local.NoteAttachmentDao
import com.iponlove.app.feature.notes.data.local.NoteAttachmentEntity
import com.iponlove.app.feature.notes.domain.model.NoteAttachment
import com.iponlove.app.feature.notes.domain.repository.NoteAttachmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class NoteAttachmentRepositoryImpl @Inject constructor(
    private val dao: NoteAttachmentDao,
    private val clock: SyncClock,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
) : NoteAttachmentRepository {

    override fun observeByNote(noteId: String): Flow<List<NoteAttachment>> =
        dao.observeByNoteId(noteId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun addAttachment(noteId: String, localPath: String): NoteAttachment {
        val id = UUID.randomUUID().toString()
        val now = clock.stamp(null)
        val position = dao.countActiveByNoteId(noteId)
        val entity = NoteAttachmentEntity(
            id = id,
            noteId = noteId,
            type = "IMAGE",
            localPath = localPath,
            url = null,
            position = position,
            createdAt = now,
            updatedAt = now,
            isDeleted = false,
            serverRev = null,
            pendingSync = true,
        )
        dao.upsert(entity)
        syncTrigger.requestPush()
        return entity.toDomain()
    }

    override suspend fun deleteAttachment(id: String) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun softDeleteAllForNote(noteId: String) {
        dao.softDeleteAllForNote(noteId, clock.stamp(null))
        syncTrigger.requestPush()
    }

    override suspend fun purgePartnerData(userId: String) =
        dao.deleteNotOwnedByUser(userId)
}
