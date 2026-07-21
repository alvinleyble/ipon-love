package com.iponlove.app.feature.notes.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulScreenTitle
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.SharedBadge
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.notes.presentation.NoteEditorViewModel.Companion.NEW_NOTE
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_LABEL: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())

/**
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 6f): a transparent-container [Scaffold] with a
 * [PlayfulScreenTitle] (the standalone-screen pattern from Analysis/Savings) and a −4° accent
 * squircle FAB; note rows become alternating-leaf glass [PlayfulCard]s. Pure reskin — pin, shared
 * badge / partner label, preview, date, and the row overflow menu are all preserved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onOpenNote: (String) -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    StartTourOnFirstVisit(TutorialTours.NOTES, TutorialTours.NOTES_COUPLE)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(Modifier.statusBarsPadding().padding(top = 10.dp, bottom = 2.dp)) {
                PlayfulScreenTitle(title = "Notes")
            }
        },
        floatingActionButton = {
            NotesFab(
                onClick = { onOpenNote(NEW_NOTE) },
                modifier = Modifier.coachMarkTarget(TutorialTargets.NOTES_ADD),
            )
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
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (pinned.isNotEmpty()) {
                            item(key = "pinned-header") { SectionHeader("Pinned") }
                            itemsIndexed(pinned, key = { _, note -> note.id }) { index, note ->
                                NoteCard(
                                    note = note,
                                    index = index,
                                    onClick = { onOpenNote(note.id) },
                                    onDelete = { viewModel.delete(note.id) },
                                    onTogglePin = { viewModel.setPinned(note.id, !note.isPinned) },
                                )
                            }
                            if (others.isNotEmpty()) {
                                item(key = "others-header") { SectionHeader("Others") }
                            }
                        }
                        itemsIndexed(others, key = { _, note -> note.id }) { index, note ->
                            NoteCard(
                                note = note,
                                index = index,
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

/** The center −4° squircle FAB, matching the global add-transaction / Savings FAB identity. */
@Composable
private fun NotesFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .rotate(-4f)
            .size(56.dp)
            .clip(LeafShapes.Fab)
            .background(colors.accent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "New note",
            tint = colors.onAccent,
            modifier = Modifier.size(27.dp).rotate(4f),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalPlayfulColors.current.textSecondary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun NoteCard(
    note: NoteListItem,
    index: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    var menuOpen by remember { mutableStateOf(false) }
    PlayfulCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
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
                                color = colors.accent,
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
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = DATE_LABEL.format(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            if (!note.isPartnerNote) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = colors.textSecondary,
                        )
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
    val colors = LocalPlayfulColors.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.EditNote,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text("No notes yet", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap + to write one.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
