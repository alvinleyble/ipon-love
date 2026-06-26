package com.iponlove.app.feature.notes.presentation

import com.iponlove.app.feature.notes.domain.model.NoteAttachment

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
    val attachments: List<NoteAttachment> = emptyList(),
    /** Current sharing state of this note. */
    val isShared: Boolean = false,
    /** True when the user is paired — share toggle is only shown in this state. */
    val isPaired: Boolean = false,
    /** The couple id, populated when [isPaired] is true. Used for shareNote calls. */
    val coupleId: String? = null,
    /** True when the note belongs to the partner; editor is read-only in this state. */
    val isPartnerNote: Boolean = false,
)
