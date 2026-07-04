package com.iponlove.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A selectable entity (account, category, type, …) rendered by [EntityChipRow] or [EntityGrid].
 * Shared across the transaction editor and recurring-rule editor so both pick accounts/categories
 * with the same icon + accent-color styling (CLAUDE.md scalability principle: shared UI in core/).
 */
data class EntityPickerOption(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val colorHex: String? = null,
)

/** Tap-to-select pills with an optional leading icon tinted by the entity's accent color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntityChipRow(
    options: List<EntityPickerOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) {
        EmptyPickerText()
        return
    }
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val accent = parseHexColor(option.colorHex) ?: MaterialTheme.colorScheme.primary
            IponFilterChip(
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
                label = { Text(option.label) },
                leadingIcon = option.icon?.let { vector ->
                    {
                        Icon(
                            imageVector = vector,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
    }
}

/** Wrapping grid of icon+color circular tiles, tap-to-select. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntityGrid(
    options: List<EntityPickerOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) {
        EmptyPickerText()
        return
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEach { option ->
            EntityTile(
                option = option,
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
            )
        }
    }
}

@Composable
private fun EntityTile(option: EntityPickerOption, selected: Boolean, onClick: () -> Unit) {
    val accent = parseHexColor(option.colorHex) ?: MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(72.dp).clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
                .border(
                    BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    CircleShape,
                ),
        ) {
            if (option.icon != null) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = option.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyPickerText() {
    Text(
        "None available",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
