package com.iponlove.app.feature.notes

import com.iponlove.app.feature.notes.data.local.NoteDao
import com.iponlove.app.feature.notes.data.local.NoteEntity
import com.iponlove.app.feature.notes.data.remote.NoteDto
import com.iponlove.app.feature.notes.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** In-memory [NoteDao] for fast JVM tests. */
class FakeNoteDao : NoteDao {
    val store = linkedMapOf<String, NoteEntity>()
    private val changes = MutableStateFlow(0)

    override fun observeNotes(): Flow<List<NoteEntity>> =
        changes.map {
            store.values
                .filter { !it.isDeleted }
                .sortedByDescending { it.updatedAt }
        }

    override suspend fun getById(id: String): NoteEntity? = store[id]

    override suspend fun upsert(note: NoteEntity) {
        store[note.id] = note
        changes.value++
    }

    override suspend fun dirtyRows(): List<NoteEntity> = store.values.filter { it.pendingSync }

    override suspend fun clearPending(ids: List<String>) {
        ids.forEach { id -> store[id]?.let { store[id] = it.copy(pendingSync = false) } }
        changes.value++
    }

    override suspend fun applyPullBatch(notes: List<NoteEntity>) {
        notes.forEach { store[it.id] = it }
        changes.value++
    }
}

fun note(
    id: String,
    title: String = "Trip plan",
    contentHtml: String = "<p>Body</p>",
) = Note(id = id, title = title, contentHtml = contentHtml)

fun noteEntity(
    id: String,
    userId: String = "user-1",
    title: String? = "Trip plan",
    content: String? = "<p>Body</p>",
    isShared: Boolean = false,
    coupleId: String? = null,
    createdAt: Instant = Instant.ofEpochMilli(1_000),
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
) = NoteEntity(
    id = id,
    userId = userId,
    title = title,
    content = content,
    isShared = isShared,
    coupleId = coupleId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = pendingSync,
)

fun noteDto(
    id: String,
    title: String? = "Trip plan",
    content: String? = "<p>Body</p>",
    isShared: Boolean = false,
    coupleId: String? = null,
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
) = NoteDto(
    id = id,
    userId = "user-1",
    title = title,
    content = content,
    isShared = isShared,
    coupleId = coupleId,
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)
