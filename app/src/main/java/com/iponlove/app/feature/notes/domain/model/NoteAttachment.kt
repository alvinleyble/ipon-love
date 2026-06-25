package com.iponlove.app.feature.notes.domain.model

/**
 * A single image attached to a note. Pure domain model.
 *
 * [localPath] is non-null while the file is on-device awaiting upload; null once uploaded.
 * [url] is non-null once uploaded to Supabase Storage; null before upload.
 * Coil loads from [localPath] (as a File) if [url] is null, otherwise from [url].
 */
data class NoteAttachment(
    val id: String,
    val noteId: String,
    val localPath: String?,
    val url: String?,
    val position: Int,
)
