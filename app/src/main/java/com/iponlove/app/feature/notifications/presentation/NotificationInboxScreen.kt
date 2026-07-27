package com.iponlove.app.feature.notifications.presentation

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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.onPlayfulSurface
import com.iponlove.app.core.ui.relativeTimeLabel
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.notifications.domain.model.AppNotification
import com.iponlove.app.feature.notifications.domain.model.NotificationCategory

/**
 * The notification inbox — the browsable home for every notification the app raises (ADR-0053).
 * A full screen reached only from the bell, with a back arrow: not a panel and not a pinnable
 * module, because it is a place you visit on purpose rather than a workspace you live in.
 *
 * Flat and newest-first: notifications are already few and already carry their own category
 * icon, so grouping would add chrome without adding information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxScreen(
    onBack: () -> Unit,
    onOpenDeepLink: (String) -> Unit,
    viewModel: NotificationInboxViewModel = hiltViewModel(),
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
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.notifications.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearAll) {
                            Text("Clear all", color = colors.textSecondary)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.notifications.isEmpty() -> EmptyInbox(Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.notifications, key = { it.id }) { notification ->
                        NotificationRow(
                            notification = notification,
                            // Unread *on entry*, not right now: opening the inbox already
                            // marked everything read, so the row's own flag would be false.
                            highlighted = notification.id in state.unreadOnEntry,
                            onClick = { notification.deepLink?.let(onOpenDeepLink) },
                            onDismiss = { viewModel.dismiss(notification.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationRow(
    notification: AppNotification,
    highlighted: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    // Keyed on the row's id so a dismissed row's swipe state is never reused by whichever row
    // slides up into its slot.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val dismissed = value != SwipeToDismissBoxValue.Settled
            if (dismissed) onDismiss()
            dismissed
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Drawn ONLY while a swipe is actually in progress. A always-on background bleeds
            // straight through the read rows — PlayfulSurface.Glass is a translucent fill — which
            // both leaks this "Dismiss" label permanently and tints read rows *more* than the
            // opaque Blush of an unread one, exactly inverting the highlight.
            val direction = dismissState.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(LeafShapes.Card)
                        .background(colors.blush)
                        .padding(horizontal = 20.dp),
                    contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    },
                ) {
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onBlush,
                    )
                }
            }
        },
    ) {
        NotificationCard(notification, highlighted, onClick)
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    val surface = if (highlighted) PlayfulSurface.Blush else PlayfulSurface.Glass
    // Ink must follow the surface, not the page. In dark mode Blush is a near-WHITE card
    // (`lerp(primaryContainer, White, 0.85f)`) while `textPrimary` is `onBackground` — near-white
    // ink meant for the dark page behind it. Hard-coding textPrimary here rendered every unread
    // row near-white on near-white; read rows hid the bug because Glass is only a faint tint over
    // the dark background, where that same ink reads fine.
    val inkPrimary = onPlayfulSurface(surface)
    val inkSecondary = if (highlighted) colors.onBlushSecondary else colors.textSecondary
    PlayfulCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = notification.deepLink != null, onClick = onClick),
        surface = surface,
        shape = LeafShapes.Card,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(LeafShapes.IconSquircle)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(notification.category),
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = inkPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = inkSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = relativeTimeLabel(notification.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = inkSecondary,
                )
            }
        }
    }
}

private fun categoryIcon(category: NotificationCategory): ImageVector = when (category) {
    NotificationCategory.BUDGET -> Icons.Filled.AccountBalanceWallet
    NotificationCategory.RECURRING -> Icons.Filled.Repeat
    NotificationCategory.COUPLE -> Icons.Filled.Favorite
    NotificationCategory.OTHER -> Icons.Filled.Notifications
}

@Composable
private fun EmptyInbox(modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "You're all caught up",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Budget alerts and other notifications will show up here.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}
