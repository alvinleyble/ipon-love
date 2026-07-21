package com.iponlove.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * A Playful Pop settings row (v1.6.7 Item 8 Slice 6g): a glass leaf-squircle [PlayfulCard] with a
 * bold [headline], optional [supporting] caption, and either a custom [trailing] slot (e.g. a
 * Switch) or — when [onClick] is set and no trailing is given — a chevron. [index] alternates the
 * leaf lean so adjacent rows within a section mirror each other, matching the entity-list screens.
 * Colors come from [LocalPlayfulColors] so the row re-tints with the active palette + light/dark.
 */
@Composable
fun SettingsRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    index: Int = 0,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            when {
                trailing != null -> {
                    Spacer(Modifier.width(12.dp))
                    trailing()
                }
                onClick != null -> {
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.textTertiary,
                    )
                }
            }
        }
    }
}

/**
 * A Playful Pop section header (v1.6.7 Item 8 Slice 6g): the Item-6 section labels kept intact
 * (typography + text), retinted to [LocalPlayfulColors].textSecondary so they read on the
 * transparent-Scaffold Playful backdrop instead of falling back to black.
 */
@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = colors.textSecondary,
        modifier = modifier,
    )
}
