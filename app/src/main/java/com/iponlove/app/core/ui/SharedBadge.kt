package com.iponlove.app.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
 * Small "Shared" pill marking a couple-owned account or category (ADR-0018) so each partner
 * can tell shared entities apart from their personal ones in the list. Restyled to a Playful Pop
 * deepPlum leaf chip with a heart bullet (v1.6.7 Item 8 Slice 3) — reused by every screen that
 * shows shared entities, so it ripples into not-yet-redesigned screens ahead of their own slice.
 */
@Composable
fun SharedBadge(modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = modifier,
        surface = PlayfulSurface.DeepPlum,
        shape = LeafShapes.Badge,
        contentPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeartBullet(colors.onDeepPlum, sizeDp = 10)
            Spacer(Modifier.width(3.dp))
            Text(
                text = "Shared",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onDeepPlum,
            )
        }
    }
}
