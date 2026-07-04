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
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.iponlove.app.BuildConfig
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.theme.paletteColorScheme
import com.iponlove.app.feature.settings.domain.model.ThemePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizeScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenCouple: () -> Unit = {},
    onOpenNavbar: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenBetaFeedback: () -> Unit = {},
    onOpenUpcomingFeatures: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: PersonalizeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val liveColorScheme = paletteColorScheme(state.draftPalette, state.draftIsDark)
    val context = LocalContext.current

    MaterialTheme(
        colorScheme = liveColorScheme,
        typography = MaterialTheme.typography,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
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
                    headlineContent = { Text("Profile") },
                    supportingContent = { Text("Display name, color & account") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenProfile),
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Security") },
                    supportingContent = { Text("PIN lock & biometric") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenSecurity),
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Couple") },
                    supportingContent = { Text("Pairing, invite code & unpair") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenCouple),
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Edit navbar") },
                    supportingContent = { Text("Choose & reorder bottom-bar shortcuts") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenNavbar),
                )
                HorizontalDivider()

                Spacer(Modifier.height(24.dp))
                Text("Support", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Help") },
                    supportingContent = { Text("FAQs and app guide") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenHelp),
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Privacy Policy") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)),
                        )
                    }),
                )
                HorizontalDivider()

                if (BuildConfig.IS_BETA_BUILD) {
                    Spacer(Modifier.height(24.dp))
                    Text("Beta", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Beta feedback") },
                        supportingContent = { Text("Report bugs or suggest improvements") },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable(onClick = onOpenBetaFeedback),
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Upcoming features") },
                        supportingContent = { Text("See what's on our roadmap") },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable(onClick = onOpenUpcomingFeatures),
                    )
                    HorizontalDivider()
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
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

private const val PRIVACY_POLICY_URL = "https://alvinleyble.github.io/ipon-love-legal/privacy-policy.html"
