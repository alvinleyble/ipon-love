package com.iponlove.app.feature.notes.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.notes.data.local.NoteDao
import com.iponlove.app.feature.notes.data.local.NoteEntity
import com.iponlove.app.feature.notes.domain.model.Note
import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [NoteRepository]. The single place every note write applies the sync
 * bookkeeping: a fresh monotonic `updated_at` (ADR-0001) and `pending_sync` (ADR-0002);
 * deletes are soft (ADR-0010).
 *
 * The editor only ever sets title + body, so an edit preserves the fields it doesn't
 * touch — ownership, `created_at`, `server_rev`, and the sharing columns (`is_shared`,
 * `couple_id`) that the Couples slice will own.
 */
class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
) : NoteRepository {

    override fun observeNotes(): Flow<List<Note>> =
        dao.observeNotes().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getNote(id: String): Note? = dao.getById(id)?.toDomain()

    override suspend fun upsertNote(note: Note) {
        val existing = dao.getById(note.id)
        val updatedAt = clock.stamp(existing?.updatedAt)
        dao.upsert(
            NoteEntity(
                id = note.id,
                userId = existing?.userId ?: currentUser.userId(),
                // Empty editor fields are stored as null to match the nullable schema columns.
                title = note.title.ifBlank { null },
                content = note.contentHtml.ifEmpty { null },
                isShared = existing?.isShared ?: false,
                coupleId = existing?.coupleId,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
    }

    override suspend fun deleteNote(id: String) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
    }
}
