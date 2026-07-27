package com.iponlove.app.feature.notifications.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * What the inbox bell needs to render itself: the unread count for the badge, and where to go.
 * Supplied once by the host at the nav root rather than threaded through every screen's
 * ViewModel — the bell is account-global chrome (unlike the money-only privacy eye), so every
 * top-level title bar shows the same one (ADR-0053).
 */
data class InboxBellState(
    val unreadCount: Int,
    val onOpen: () -> Unit,
)

/**
 * Null outside the signed-in nav host — previews, onboarding, and the auth graph have no inbox,
 * and [InboxBell] simply renders nothing there rather than each call site guarding.
 */
val LocalInboxBell: ProvidableCompositionLocal<InboxBellState?> = compositionLocalOf { null }

@Composable
fun ProvideInboxBell(state: InboxBellState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInboxBell provides state, content = content)
}

/**
 * The bell + unread badge. Rendered automatically by
 * [com.iponlove.app.core.ui.PlayfulScreenTitle], which is every top-level module's title bar —
 * that is what makes the rollout a single pass instead of a per-screen edit.
 */
@Composable
fun InboxBell(modifier: Modifier = Modifier) {
    val bell = LocalInboxBell.current ?: return
    val colors = LocalPlayfulColors.current
    IconButton(onClick = bell.onOpen, modifier = modifier) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = if (bell.unreadCount > 0) {
                    "Notifications, ${bell.unreadCount} unread"
                } else {
                    "Notifications"
                },
                tint = colors.textSecondary,
            )
            if (bell.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 6.dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(LeafShapes.Badge)
                        .background(colors.accent)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        // Past 99 the exact number stops meaning anything actionable.
                        text = if (bell.unreadCount > 99) "99+" else bell.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onAccent,
                    )
                }
            }
        }
    }
}
