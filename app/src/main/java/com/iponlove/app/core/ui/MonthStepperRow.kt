package com.iponlove.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * A calendar-month label with prev/next step arrows — the same shape as Analysis's period
 * stepper, shared here since Records and Combined both page a single calendar month at a
 * time (ADR-0032) with no period-switching UI of their own.
 */
@Composable
fun MonthStepperRow(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canGoNext: Boolean = true,
    canGoPrevious: Boolean = true,
    onPreviousLocked: () -> Unit = {},
) {
    val colors = LocalPlayfulColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // At the DEEP_HISTORY back-wall (canGoPrevious = false, locked) the ← becomes a lock that
        // routes to the paywall instead of stepping — an "Unlock older history" affordance (§10.3),
        // not a dead disabled button. Dormant/premium: canGoPrevious is true, so it steps as before.
        IconButton(onClick = if (canGoPrevious) onPrevious else onPreviousLocked) {
            if (canGoPrevious) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous month", tint = colors.textSecondary)
            } else {
                Icon(Icons.Filled.Lock, contentDescription = "Unlock older history", tint = colors.textSecondary)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = colors.textSecondary.copy(alpha = if (canGoNext) 1f else 0.4f),
            )
        }
    }
}
