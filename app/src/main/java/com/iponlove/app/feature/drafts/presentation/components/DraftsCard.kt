package com.iponlove.app.feature.drafts.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.drafts.presentation.DraftsCardViewModel

/**
 * The pinned "Drafts (N)" card on Records — the anti-graveyard mechanism (ADR-0066 decision 10),
 * and a direct clone of `PendingConfirmationsCard`'s shape: pinned above the month-scoped list
 * (a parked draft is not a row of any particular month) and **renders nothing when the queue is
 * empty**, so it is safe to drop unconditionally at the top of the screen.
 *
 * It is both the entry point *and* the reminder, which is why it costs nothing extra and why no
 * notification was built: ADR-0048 decision 6 chose exactly this passive shape for pending
 * recurring occurrences, and drafts inherit that ruling rather than reopen it.
 */
@Composable
fun DraftsCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DraftsCardViewModel = hiltViewModel(),
) {
    val count by viewModel.draftCount.collectAsState()
    if (count == 0) return

    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
        contentPadding = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.BookmarkBorder,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Drafts ($count)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Text(
                    text = if (count == 1) {
                        "One unfinished entry waiting for you."
                    } else {
                        "$count unfinished entries waiting for you."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
    }
}
