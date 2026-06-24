package com.iponlove.app.feature.notes.presentation

import java.time.Instant

/** Screen state for the notes list. */
data class NotesUiState(
    val isLoading: Boolean = true,
    val notes: List<NoteListItem> = emptyList(),
)

/**
 * A note as the list renders it: [preview] is the body reduced to plain text (the markup
 * never reaches the UI), [title] falls back to "Untitled" so every row has a heading.
 */
data class NoteListItem(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAt: Instant,
    val isShared: Boolean,
)
