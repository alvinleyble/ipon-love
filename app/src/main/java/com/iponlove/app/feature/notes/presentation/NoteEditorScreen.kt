package com.iponlove.app.feature.notes.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.iponlove.app.core.ui.CapReachedSheet
import com.iponlove.app.core.ui.FullScreenImageDialog
import com.iponlove.app.core.ui.IponFilterChip
import com.iponlove.app.core.ui.SharedBadge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.iponlove.app.feature.notes.domain.model.NoteAttachment
import com.iponlove.app.feature.notes.domain.usecase.NoteCharLimit
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    onOpenPremium: () -> Unit = {},
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val richTextState = rememberRichTextState()
    // Draft lives in rememberSaveable so the in-progress edit survives rotation AND process death
    // (Slice 1B). null = not yet seeded; once seeded these hold the live edit. The RichTextState
    // itself isn't saveable, so its HTML is mirrored into `draftHtml` and re-seeded on recreation.
    var title by rememberSaveable { mutableStateOf<String?>(null) }
    var draftHtml by rememberSaveable { mutableStateOf<String?>(null) }
    var contentSeeded by remember { mutableStateOf(false) }
    // Attachment tapped for full-screen viewing: a remote URL string or local File, else null.
    var viewerImage by remember { mutableStateOf<Any?>(null) }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.addImage(it) } }

    // The body length at seed time — the T1 freeze floor: an existing note longer than a lowered
    // tier cap is frozen at this length, never truncated on open (see NoteCharLimit.effectiveLimit).
    var seededLength by remember { mutableStateOf(0) }

    LaunchedEffect(state.loaded) {
        if (!state.loaded) return@LaunchedEffect
        // Prefer a restored draft over the loaded DB values so recreation doesn't clobber edits.
        if (title == null) title = state.initialTitle
        if (!contentSeeded) {
            richTextState.setHtml(draftHtml ?: state.initialHtml)
            seededLength = richTextState.annotatedString.text.length
            contentSeeded = true
        }
    }

    // Mirror every rich-text edit into the saveable holder so it's captured for process death.
    LaunchedEffect(contentSeeded) {
        if (contentSeeded) {
            snapshotFlow { richTextState.toHtml() }.collect { draftHtml = it }
        }
    }

    // Body character limit: the entitlement-resolved tier cap (S10 — free 5k / premium 50k), but
    // never below what the note already holds (freeze floor). Counts the reader-visible text
    // (WYSIWYG), and hard-blocks growth by trimming any overflow at the cursor back to the cap the
    // instant an edit overshoots — so a persisted note can never *grow* past it. Re-keyed on
    // charLimit so a live enforcement/entitlement flip re-evaluates against the new ceiling.
    val charLimit = NoteCharLimit.effectiveLimit(state.noteCharLimit, seededLength)
    val bodyLength by remember { derivedStateOf { richTextState.annotatedString.text.length } }
    LaunchedEffect(contentSeeded, charLimit) {
        if (!contentSeeded) return@LaunchedEffect
        snapshotFlow { richTextState.annotatedString.text.length }.collect { length ->
            val overflow = NoteCharLimit.overflow(length, charLimit)
            if (overflow > 0) {
                // Strip exactly the characters that overshot, ending at the cursor — trims a paste
                // to fit and rejects the extra keystroke at the ceiling, leaving the caret at the cap.
                val end = richTextState.selection.end
                val start = (end - overflow).coerceAtLeast(0)
                richTextState.removeTextRange(TextRange(start, end))
            }
        }
    }

    // The nav bar arrow and the Android system back both save on exit. Guard on `contentSeeded`
    // so a back-press during load can't fire save() with empty content (which would hit the
    // blank-note branch and delete an existing note) — before seeding, just leave without saving.
    val saveAndExit: () -> Unit = {
        if (contentSeeded) {
            viewModel.save(title.orEmpty(), richTextState.toHtml(), onBack)
        } else {
            onBack()
        }
    }
    BackHandler { saveAndExit() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.isPartnerNote -> "Partner's note"
                            state.isNew -> "New note"
                            else -> "Edit note"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = saveAndExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and close")
                    }
                },
                actions = {
                    if (!state.isPartnerNote) {
                        if (state.isPaired) {
                            if (state.isShared) {
                                SharedBadge(
                                    modifier = Modifier.clickable(onClick = { viewModel.toggleShared() }),
                                )
                            } else {
                                IconButton(onClick = { viewModel.toggleShared() }) {
                                    Icon(
                                        imageVector = Icons.Filled.FavoriteBorder,
                                        contentDescription = "Share note with partner",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                pickMedia.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add image")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.loaded ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.missing ->
                    Text(
                        text = "This note no longer exists.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )

                else -> Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    OutlinedTextField(
                        value = title.orEmpty(),
                        onValueChange = { if (!state.isPartnerNote) title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        readOnly = state.isPartnerNote,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.attachments.isNotEmpty()) {
                        AttachmentStrip(
                            attachments = state.attachments,
                            onDelete = { viewModel.removeAttachment(it) },
                            onView = { viewerImage = it },
                            showDeleteButtons = !state.isPartnerNote,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                    if (!state.isPartnerNote) {
                        FormattingToolbar(richTextState)
                        if (NoteCharLimit.shouldShowCounter(bodyLength, charLimit)) {
                            Text(
                                text = "%,d / %,d".format(bodyLength, charLimit),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (NoteCharLimit.isOver(bodyLength, charLimit)) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(vertical = 2.dp),
                            )
                        }
                    }
                    OutlinedRichTextEditor(
                        state = richTextState,
                        readOnly = state.isPartnerNote,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    viewerImage?.let { image ->
        FullScreenImageDialog(
            model = image,
            contentDescription = "Attached image full size",
            onDismiss = { viewerImage = null },
        )
    }

    state.upsell?.let { prompt ->
        CapReachedSheet(
            prompt = prompt,
            onDismiss = viewModel::dismissUpsell,
            onUpgrade = { viewModel.onUpsellUpgrade(); onOpenPremium() },
        )
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<NoteAttachment>,
    onDelete: (String) -> Unit,
    onView: (Any) -> Unit,
    showDeleteButtons: Boolean = true,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            val model: Any? = attachment.url ?: attachment.localPath?.let { File(it) }
            Box {
                AsyncImage(
                    model = model,
                    contentDescription = "Attached image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = model != null) { model?.let(onView) },
                )
                if (showDeleteButtons) {
                    SmallFloatingActionButton(
                        onClick = { onDelete(attachment.id) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(20.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove image",
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormattingToolbar(state: com.mohamedrejeb.richeditor.model.RichTextState) {
    val span = state.currentSpanStyle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IponFilterChip(
            selected = span.fontWeight == FontWeight.Bold,
            onClick = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
            label = { Text("B", fontWeight = FontWeight.Bold) },
        )
        IponFilterChip(
            selected = span.fontStyle == FontStyle.Italic,
            onClick = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
            label = { Text("I", fontStyle = FontStyle.Italic) },
        )
        IponFilterChip(
            selected = span.textDecoration == TextDecoration.Underline,
            onClick = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
            label = { Text("U", textDecoration = TextDecoration.Underline) },
        )
        AssistChip(
            onClick = { state.toggleUnorderedList() },
            label = { Text("• List") },
        )
        AssistChip(
            onClick = { state.toggleOrderedList() },
            label = { Text("1. List") },
        )
    }
}
