package com.iponlove.app.feature.notes.data

import com.iponlove.app.feature.notes.data.local.NoteEntity
import com.iponlove.app.feature.notes.data.remote.NoteDto
import com.iponlove.app.feature.notes.domain.model.Note

/** Entity ↔ Domain ↔ DTO conversions. Pure functions — unit-tested. */

/** Normalizes the nullable `title`/`content` columns to "" at the domain boundary. */
fun NoteEntity.toDomain(): Note = Note(
    id = id,
    title = title.orEmpty(),
    contentHtml = content.orEmpty(),
    isShared = isShared,
    updatedAt = updatedAt,
)

/** Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002). */
fun NoteEntity.toDto(): NoteDto = NoteDto(
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
)

/**
 * DTO → Entity for a pulled row: server-canonical, so `pendingSync = false`, carrying the
 * server-assigned `serverRev` the cursor advances on (ADR-0002).
 */
fun NoteDto.toEntity(): NoteEntity = NoteEntity(
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
    pendingSync = false,
)
