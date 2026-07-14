package com.iponlove.app.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.theme.paletteColorScheme
import com.iponlove.app.feature.settings.domain.model.ThemePalette

/**
 * Appearance sub-screen (v1.6.5 Item 34): palette grid + light/dark switch + Apply. Wraps its
 * content in a live-preview [MaterialTheme] so tapping a swatch or the switch re-themes the screen
 * instantly (ADR-0014 live preview); nothing persists until Apply. Lifted verbatim out of
 * PersonalizeScreen — this is the only surface that still needs the local theme wrapper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // Render the *effective* palette (G8): a locked Premium palette shows as the free default here
    // and app-wide, while the chosen one stays saved and auto-restores on unlock.
    val liveColorScheme = paletteColorScheme(
        state.draftPalette.effective(state.paletteLocked),
        state.draftIsDark,
    )

    MaterialTheme(
        colorScheme = liveColorScheme,
        typography = MaterialTheme.typography,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Appearance") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text("Color Palette", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                PaletteGrid(
                    palettes = ThemePalette.entries,
                    // Highlight the effective (rendered) palette, not the possibly-locked chosen one.
                    selected = state.draftPalette.effective(state.paletteLocked),
                    locked = state.paletteLocked,
                    onSelect = viewModel::selectPalette,
                    onLockedTap = { onOpenPremium(viewModel.onLockedPaletteTap()) },
                )

                Spacer(Modifier.height(28.dp))
                Text("Mode", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(if (state.draftIsDark) "Dark" else "Light", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = state.draftIsDark,
                        onCheckedChange = viewModel::toggleDarkMode,
                    )
                }

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.saved) "Saved!" else "Apply")
                }
            }
        }
    }
}

@Composable
private fun PaletteGrid(
    palettes: List<ThemePalette>,
    selected: ThemePalette,
    locked: Boolean,
    onSelect: (ThemePalette) -> Unit,
    onLockedTap: () -> Unit,
) {
    val rowSize = 3
    palettes.chunked(rowSize).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            row.forEach { palette ->
                val isLocked = locked && !palette.isFree
                PaletteSwatch(
                    palette = palette,
                    isSelected = palette == selected && !isLocked,
                    isLocked = isLocked,
                    onClick = { if (isLocked) onLockedTap() else onSelect(palette) },
                    modifier = Modifier.weight(1f),
                )
            }
            // fill empty cells in last row
            repeat(rowSize - row.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seedColor = Color(android.graphics.Color.parseColor(palette.seedHex))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(seedColor)
                // Dim a locked (Premium) swatch so the lock badge reads clearly.
                .then(if (isLocked) Modifier.alpha(0.45f) else Modifier)
                .then(
                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    else Modifier
                ),
        ) {
            when {
                isLocked -> Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Premium",
                    tint = Color.White,
                )
                isSelected -> Text("✓", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(palette.label, style = MaterialTheme.typography.labelSmall)
    }
}
