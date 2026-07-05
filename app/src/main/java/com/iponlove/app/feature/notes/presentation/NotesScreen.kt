package com.iponlove.app.feature.notes.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.SharedBadge
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.feature.notes.presentation.NoteEditorViewModel.Companion.NEW_NOTE
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_LABEL: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onOpenNote: (String) -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    StartTourOnFirstVisit(TutorialTours.NOTES, TutorialTours.NOTES_COUPLE)
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Notes") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onOpenNote(NEW_NOTE) },
                modifier = Modifier.coachMarkTarget(TutorialTargets.NOTES_ADD),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.notes.isEmpty() ->
                    EmptyState(Modifier.align(Alignment.Center))

                else -> {
                    // observeNotes already orders pinned-first, so partitioning keeps order.
                    val pinned = state.notes.filter { it.isPinned }
                    val others = state.notes.filterNot { it.isPinned }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (pinned.isNotEmpty()) {
                            item(key = "pinned-header") { SectionHeader("Pinned") }
                            items(pinned, key = { it.id }) { note ->
                                NoteCard(
                                    note = note,
                                    onClick = { onOpenNote(note.id) },
                                    onDelete = { viewModel.delete(note.id) },
                                    onTogglePin = { viewModel.setPinned(note.id, !note.isPinned) },
                                )
                            }
                            if (others.isNotEmpty()) {
                                item(key = "others-header") { SectionHeader("Others") }
                            }
                        }
                        items(others, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onOpenNote(note.id) },
                                onDelete = { viewModel.delete(note.id) },
                                onTogglePin = { viewModel.setPinned(note.id, !note.isPinned) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun NoteCard(
    note: NoteListItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    when {
                        note.isPartnerNote -> {
                            val label = "From ${note.partnerName ?: "Partner"}"
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        note.isShared -> SharedBadge()
                    }
                }
                if (note.preview.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = DATE_LABEL.format(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!note.isPartnerNote) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                            onClick = { menuOpen = false; onTogglePin() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No notes yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap + to write one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
