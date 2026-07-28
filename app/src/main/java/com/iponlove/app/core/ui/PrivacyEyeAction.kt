package com.iponlove.app.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * The privacy eye as a standard header action (Item 16) — a stateless component reading the
 * already-provided [LocalPrivacyMode] and writing through [LocalTogglePrivacyMode], so any module
 * header can drop this in without threading `isPrivacyModeOn`/`onTogglePrivacyMode` through that
 * feature's own ViewModel. Masking itself is global (house rule — the eye never does a local
 * reveal), so one shared control is correct wherever a header wants it.
 */
@Composable
fun PrivacyEyeAction(modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    val isPrivacyModeOn = LocalPrivacyMode.current
    val togglePrivacyMode = LocalTogglePrivacyMode.current
    IconButton(onClick = togglePrivacyMode, modifier = modifier) {
        Icon(
            imageVector = if (isPrivacyModeOn) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (isPrivacyModeOn) "Show amounts" else "Hide amounts",
            tint = colors.textSecondary,
        )
    }
}
