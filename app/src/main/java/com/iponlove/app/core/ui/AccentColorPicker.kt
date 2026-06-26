package com.iponlove.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One selectable couple-attribution color. Shared by the pairing flow (slice L) and the
 * Profile screen (ADR-0016) so both offer the same swatches without duplication.
 */
data class AccentColorOption(val hex: String, val label: String, val color: Color)

val AccentColorOptions = listOf(
    AccentColorOption("#1565C0", "Blue", Color(0xFF1565C0)),
    AccentColorOption("#C2185B", "Pink", Color(0xFFC2185B)),
    AccentColorOption("#F9A825", "Yellow", Color(0xFFF9A825)),
    AccentColorOption("#C62828", "Red", Color(0xFFC62828)),
    AccentColorOption("#6A1B9A", "Purple", Color(0xFF6A1B9A)),
    AccentColorOption("#2E7D32", "Green", Color(0xFF2E7D32)),
)

/** A labelled row of attribution-color swatches; [selectedHex] gets a ring border. */
@Composable
fun AccentColorRow(
    selectedHex: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Your color",
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotEmpty()) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AccentColorOptions.forEach { option ->
                AccentColorSwatch(
                    color = option.color,
                    label = option.label,
                    selected = selectedHex == option.hex,
                    enabled = enabled,
                    onClick = { onSelect(option.hex) },
                )
            }
        }
    }
}

@Composable
private fun AccentColorSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    else Modifier
                ),
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
