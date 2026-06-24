package com.iponlove.app.feature.notes.presentation

/**
 * Editor state. [loaded] gates the screen until the seed values are ready (an existing note
 * loads async); [initialTitle]/[initialHtml] seed the title field and `RichTextState` once.
 * [missing] means the note was deleted elsewhere before it opened.
 */
data class NoteEditorUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val missing: Boolean = false,
    val noteId: String? = null,
    val initialTitle: String = "",
    val initialHtml: String = "",
)
