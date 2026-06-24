package com.iponlove.app.feature.notes.data

import com.iponlove.app.feature.notes.data.local.NoteEntity
import com.iponlove.app.feature.notes.data.remote.PartnerNoteDto

/**
 * Partner-view row → Entity (ADR-0005). The view nulls `title`/`content` when the note is
 * unshared or deleted; such rows are hard-deleted on apply. `title`/`content` are nullable
 * even for visible notes, so they pass straight through. `created_at` is absent from the view.
 */
fun PartnerNoteDto.toEntity(): NoteEntity = NoteEntity(
    id = id,
    userId = userId,
    title = title,
    content = content,
    isShared = isShared,
    coupleId = coupleId,
    createdAt = updatedAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
