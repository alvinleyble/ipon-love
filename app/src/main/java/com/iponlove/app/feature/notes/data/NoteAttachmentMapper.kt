package com.iponlove.app.feature.notes.data

import com.iponlove.app.feature.notes.data.local.NoteAttachmentEntity
import com.iponlove.app.feature.notes.data.remote.NoteAttachmentDto
import com.iponlove.app.feature.notes.data.remote.PartnerNoteAttachmentDto
import com.iponlove.app.feature.notes.domain.model.NoteAttachment
import java.time.Instant

/** Entity ↔ Domain ↔ DTO conversions for note attachments. Pure functions — unit-tested. */

fun NoteAttachmentEntity.toDomain(): NoteAttachment = NoteAttachment(
    id = id,
    noteId = noteId,
    localPath = localPath,
    url = url,
    position = position,
)

fun NoteAttachmentEntity.toDto(): NoteAttachmentDto = NoteAttachmentDto(
    id = id,
    noteId = noteId,
    storageUrl = url ?: error("toDto called on un-uploaded attachment $id"),
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

fun NoteAttachmentDto.toEntity(): NoteAttachmentEntity = NoteAttachmentEntity(
    id = id,
    noteId = noteId,
    type = "IMAGE",
    localPath = null,
    url = storageUrl,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)

fun PartnerNoteAttachmentDto.toEntity(): NoteAttachmentEntity = NoteAttachmentEntity(
    id = id,
    noteId = noteId,
    type = "IMAGE",
    localPath = null,
    url = storageUrl,
    position = position,
    createdAt = Instant.EPOCH, // not exposed by the partner view
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
