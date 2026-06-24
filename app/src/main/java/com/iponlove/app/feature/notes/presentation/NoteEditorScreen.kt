package com.iponlove.app.feature.notes.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor

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

    // Seed the title field and editor once the note has loaded (existing notes load async).
    LaunchedEffect(state.loaded) {
        if (state.loaded && !seeded) {
            title = state.initialTitle
            richTextState.setHtml(state.initialHtml)
            seeded = true
        }
    }

    val saveAndExit: () -> Unit = { viewModel.save(title, richTextState.toHtml(), onBack) }
    BackHandler(onBack = saveAndExit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New note" else "Edit note") },
                navigationIcon = {
                    IconButton(onClick = saveAndExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and close")
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
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FormattingToolbar(richTextState)
                    OutlinedRichTextEditor(
                        state = richTextState,
                        modifier = Modifier.fillMaxSize(),
                    )
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
        FilterChip(
            selected = span.fontWeight == FontWeight.Bold,
            onClick = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
            label = { Text("B", fontWeight = FontWeight.Bold) },
        )
        FilterChip(
            selected = span.fontStyle == FontStyle.Italic,
            onClick = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
            label = { Text("I", fontStyle = FontStyle.Italic) },
        )
        FilterChip(
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
