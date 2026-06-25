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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.theme.paletteColorScheme
import com.iponlove.app.feature.settings.domain.model.ThemePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizeScreen(
    onBack: () -> Unit,
    onOpenSecurity: () -> Unit = {},
    viewModel: PersonalizeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val liveColorScheme = paletteColorScheme(state.draftPalette, state.draftIsDark)

    MaterialTheme(
        colorScheme = liveColorScheme,
        typography = MaterialTheme.typography,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Personalize") },
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
                    selected = state.draftPalette,
                    onSelect = viewModel::selectPalette,
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

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Security") },
                    supportingContent = { Text("PIN lock & biometric") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenSecurity),
                )
            }
        }
    }
}

@Composable
private fun PaletteGrid(
    palettes: List<ThemePalette>,
    selected: ThemePalette,
    onSelect: (ThemePalette) -> Unit,
) {
    val rowSize = 3
    palettes.chunked(rowSize).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            row.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    isSelected = palette == selected,
                    onClick = { onSelect(palette) },
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
                .then(
                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    else Modifier
                ),
        ) {
            if (isSelected) {
                Text("✓", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(palette.label, style = MaterialTheme.typography.labelSmall)
    }
}
