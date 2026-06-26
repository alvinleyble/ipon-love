package com.iponlove.app.core.ui.icons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Scrollable grid of icons the user can pick from. Used in both category and account
 * editor dialogs. [tintColor] is the currently-selected entity color (or theme primary),
 * applied to the selected icon and its highlight ring.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(
    icons: Map<String, ImageVector>,
    selectedKey: String?,
    tintColor: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .height(184.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icons.forEach { (key, vector) ->
            IconCell(
                vector = vector,
                key = key,
                selected = key == selectedKey,
                tintColor = tintColor,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun IconCell(
    vector: ImageVector,
    key: String,
    selected: Boolean,
    tintColor: Color,
    onSelect: (String) -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) tintColor.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) tintColor else MaterialTheme.colorScheme.outlineVariant,
                ),
                CircleShape,
            )
            .clickable { onSelect(key) }
            .padding(10.dp),
    ) {
        Icon(
            imageVector = vector,
            contentDescription = key,
            tint = if (selected) tintColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
