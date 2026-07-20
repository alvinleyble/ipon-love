package com.iponlove.app.feature.recurring.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.recurring.domain.model.UpcomingOccurrence
import com.iponlove.app.feature.recurring.presentation.ComingUpViewModel
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.time.format.DateTimeFormatter

/**
 * The Records "Coming up" preview (Item 37, Slice 2) — the premium forecast layer's forward
 * glance. Renders nothing when the window is empty, so it's safe to drop unconditionally below
 * the "To confirm" card. When unlocked (dormant/premium) it's a read-only list of upcoming
 * income + bills; when locked (`RECURRING_FORECAST` enforced without access) it degrades to a
 * count-only upsell teaser that routes to the paywall — never revealing the amounts.
 *
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 6a): glass [PlayfulCard] container (same
 * "To confirm" recipe, right above); amounts move onto the derived `PlayfulColors.semantic` ramp,
 * replacing the old hardcoded `IncomeColor`/`colorScheme.error`.
 */
@Composable
fun ComingUpCard(
    onOpenPremium: (source: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComingUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    if (state.items.isEmpty()) return

    if (state.locked) {
        ComingUpTeaser(
            count = state.items.size,
            onClick = { onOpenPremium(viewModel.onUpsell()) },
            modifier = modifier,
        )
        return
    }

    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
        contentPadding = 16.dp,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.width(20.dp).height(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Coming up",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
            }
            state.items.forEach { item ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.hairline)
                UpcomingRow(item)
            }
        }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingOccurrence) {
    val colors = LocalPlayfulColors.current
    val isIncome = item.type == TransactionType.INCOME
    val kindLabel = if (isIncome) "Income" else "Bill"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.categoryName,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$kindLabel due ${item.date.format(DATE_FORMAT)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Text(
            text = (if (isIncome) "+" else "−") + money(item.amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isIncome) colors.semantic.income else colors.semantic.negative,
        )
    }
}

@Composable
private fun ComingUpTeaser(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = colors.textSecondary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Coming up",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Text(
                    text = "$count upcoming ${if (count == 1) "item" else "items"} — see your income & bills ahead with Premium.",
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

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
