package com.iponlove.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.notifications.presentation.InboxBell

/**
 * The Playful Pop screen title (v1.6.7 Item 8): a bold 30sp title with a small tilted accent heart
 * beside it, plus an optional trailing [actions] slot (e.g. a privacy eye). Colors from the derived
 * [LocalPlayfulColors].
 *
 * Every top-level module's title bar goes through here, so the **notification bell** is rendered
 * here too rather than added screen by screen — that is what makes ADR-0053's "bell on every
 * top-level screen" a single rollout. It leads the action cluster (bell first, then the screen's
 * own [actions]) so per-screen icons keep their existing order, and it self-hides wherever no
 * inbox is provided. Set [showInboxBell] to false for a title bar that should not carry it.
 */
@Composable
fun PlayfulScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    showInboxBell: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPlayfulColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = colors.textPrimary,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(20.dp).rotate(-8f),
        )
        if (actions != null || showInboxBell) {
            Spacer(Modifier.weight(1f))
            if (showInboxBell) InboxBell()
            actions?.invoke()
        }
    }
}

/** A small filled-heart accent bullet in [color] — replaces the generic dot/marker throughout. */
@Composable
fun HeartBullet(color: Color, modifier: Modifier = Modifier, sizeDp: Int = 12) {
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = color,
        modifier = modifier.size(sizeDp.dp),
    )
}
