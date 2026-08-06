package com.iponlove.app.feature.drafts.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import java.io.File

/**
 * The parking area (ADR-0066) — everything the user started and didn't finish, oldest first.
 *
 * **One list, not two tabs.** Alvin's original request was Draft / w/ Receipts; that was overturned
 * at the grill with his approval (decision 9), because settling a draft means tapping it back into
 * the New transaction form *either way*, so the second tab bought a filter rather than a different
 * action — and, since scanning is fully paywalled, it would have been a premium-only surface on a
 * screen whose entire purpose is removing friction. A receipt thumbnail on the row says which
 * drafts have photos at a glance, which is what the tab was actually for.
 *
 * Reached from the pinned "Drafts (N)" card on Records, nested in the Records graph (ADR-0033).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    onBack: () -> Unit,
    onOpenDraft: (String) -> Unit,
    viewModel: DraftsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalPlayfulColors.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                    actionIconContentColor = colors.textSecondary,
                ),
                title = { Text(if (state.rows.isEmpty()) "Drafts" else "Drafts (${state.rows.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.rows.isEmpty() -> EmptyDrafts(Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        DraftCard(
                            row = row,
                            onOpen = { onOpenDraft(row.id) },
                            onDelete = { viewModel.requestDelete(row) },
                        )
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete this draft?") },
            text = { Text("\"${row.title}\" and any photo you attached to it will be removed.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun DraftCard(row: DraftRow, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DraftThumbnail(row)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    // The mild pressure that keeps the queue from becoming a graveyard — the only
                    // pressure there is, by design (decision 10).
                    text = if (row.receiptsOnOtherDevice) {
                        "${row.ageLabel} · 📷 ${row.receiptCountLabel()} — on your other device"
                    } else {
                        row.ageLabel
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    // A draft with no amount yet says so, rather than reading as a real ₱0 entry.
                    text = row.amount?.let { money(it) } ?: "No amount yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (row.amount == null) colors.textTertiary else colors.textPrimary,
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete draft",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** The receipt thumbnail that replaced the "w/ Receipts" tab (decision 9). */
@Composable
private fun DraftThumbnail(row: DraftRow) {
    val colors = LocalPlayfulColors.current
    val path = row.thumbnailPath
    if (path != null) {
        AsyncImage(
            model = File(path),
            contentDescription = "Draft receipt",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(LeafShapes.IconSquircle),
        )
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(LeafShapes.IconSquircle)
                .background(colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (row.receiptsOnOtherDevice) {
                    Icons.Filled.PhotoCamera
                } else {
                    Icons.Filled.BookmarkBorder
                },
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyDrafts(modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.BookmarkBorder,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Nothing parked",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Start a transaction and tap \"Save as draft\" to finish it later.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

/** "1 receipt" / "2 receipts" — the count syncs even though the photos don't (decision 4). */
private fun DraftRow.receiptCountLabel(): String =
    if (receiptCount == 1) "1 receipt" else "$receiptCount receipts"
