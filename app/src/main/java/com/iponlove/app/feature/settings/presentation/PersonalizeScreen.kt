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
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.theme.paletteColorScheme
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import com.iponlove.app.feature.settings.domain.model.ThemePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizeScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenPremium: (source: String) -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenCouple: () -> Unit = {},
    onOpenNavbar: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenBetaFeedback: () -> Unit = {},
    onOpenUpcomingFeatures: () -> Unit = {},
    onReplayTutorial: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: PersonalizeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // Render the *effective* palette (G8): a locked Premium palette shows as the free default here
    // and app-wide, while the chosen one stays saved and auto-restores on unlock.
    val liveColorScheme = paletteColorScheme(
        state.draftPalette.effective(state.paletteLocked),
        state.draftIsDark,
    )
    val context = LocalContext.current

    // Couple-scoped Settings tour — no-ops until paired (ADR-0038); anchors to the Couple entry.
    StartTourOnFirstVisit(TutorialTours.COUPLE_SETTINGS)

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
                // Dormant until enforcement flips ON (paywall S5 / Item 12): the row is absent
                // entirely while the paywall ships dormant, so a normal walkthrough shows nothing.
                if (state.showPremiumEntry) {
                    ListItem(
                        headlineContent = { Text("Premium") },
                        supportingContent = {
                            Text(
                                if (state.isPremium) "Active — thank you!"
                                else "Unlock more of Love, Ipon",
                            )
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onOpenPremium("settings") },
                    )
                    HorizontalDivider()
                }
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
                    modifier = Modifier
                        .coachMarkTarget(TutorialTargets.SETTINGS_COUPLE)
                        .clickable(onClick = onOpenCouple),
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
                    headlineContent = { Text("Replay tutorial") },
                    supportingContent = { Text("Take the quick app walkthrough again") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onReplayTutorial),
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

private const val PRIVACY_POLICY_URL = "https://alvinleyble.github.io/ipon-love-legal/privacy-policy.html"
