package com.iponlove.app.feature.notes.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.iponlove.app.core.ui.IponFilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.iponlove.app.feature.notes.domain.model.NoteAttachment
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val richTextState = rememberRichTextState()
    var title by remember { mutableStateOf("") }
    var seeded by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.addImage(it) } }

    LaunchedEffect(state.loaded) {
        if (state.loaded && !seeded) {
            title = state.initialTitle
            richTextState.setHtml(state.initialHtml)
            seeded = true
        }
    }

    val saveAndExit: () -> Unit = { viewModel.save(title, richTextState.toHtml(), onBack) }

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
                            IconButton(onClick = { viewModel.toggleShared() }) {
                                Icon(
                                    imageVector = if (state.isShared) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = if (state.isShared) "Unshare note" else "Share note with partner",
                                    tint = if (state.isShared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
                        value = title,
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
                            showDeleteButtons = !state.isPartnerNote,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                    if (!state.isPartnerNote) {
                        FormattingToolbar(richTextState)
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
}

@Composable
private fun AttachmentStrip(
    attachments: List<NoteAttachment>,
    onDelete: (String) -> Unit,
    showDeleteButtons: Boolean = true,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            Box {
                AsyncImage(
                    model = if (attachment.url != null) {
                        attachment.url
                    } else {
                        attachment.localPath?.let { File(it) }
                    },
                    contentDescription = "Attached image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
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
