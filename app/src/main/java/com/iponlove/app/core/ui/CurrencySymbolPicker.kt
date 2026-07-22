package com.iponlove.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol

/**
 * The display-currency swatch grid (Item 18) — shared by the Settings → Personalize picker and the
 * onboarding Currency step (Item 27) so both offer the same swatches without duplication, mirroring
 * how [AccentColorRow] is shared between the pairing flow and the Profile screen.
 */
@Composable
fun CurrencyGrid(
    symbols: List<CurrencySymbol>,
    selected: CurrencySymbol,
    onSelect: (CurrencySymbol) -> Unit,
) {
    val rowSize = 4
    symbols.chunked(rowSize).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) {
            row.forEach { symbol ->
                CurrencySwatch(
                    symbol = symbol,
                    isSelected = symbol == selected,
                    onClick = { onSelect(symbol) },
                    modifier = Modifier.weight(1f),
                )
            }
            // fill empty cells in the last row so swatches keep their column width
            repeat(rowSize - row.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CurrencySwatch(
    symbol: CurrencySymbol,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .then(
                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                ),
        ) {
            Text(
                symbol.glyph,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            symbol.code,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
