package com.iponlove.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Material-design colors curated for PH personal-finance category/account labels. */
val EntityColorOptions = listOf(
    "#E53935", "#D81B60", "#8E24AA", "#3949AB",
    "#1E88E5", "#039BE5", "#00897B", "#43A047",
    "#7CB342", "#FB8C00", "#F4511E", "#6D4C41",
    "#546E7A", "#F4B400",
)

/**
 * A labeled swatch grid for picking an entity color (category or account). Different
 * from [AccentColorRow], which is for couple-attribution colors (ADR-0014).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntityColorPicker(
    selectedHex: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Color",
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EntityColorOptions.forEach { hex ->
            val color = parseHexColor(hex) ?: return@forEach
            val selected = hex == selectedHex
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier
                    )
                    .clickable { onSelect(hex) },
            )
        }
    }
}
