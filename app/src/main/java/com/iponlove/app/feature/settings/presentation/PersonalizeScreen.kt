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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
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
    var showBudgetStartDayDialog by remember { mutableStateOf(false) }

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
                Text("Privacy", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hide amounts", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Mask money values on screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.privacyModeEnabled,
                        onCheckedChange = viewModel::setPrivacyMode,
                    )
                }

                Spacer(Modifier.height(28.dp))
                Text("Currency", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Symbol shown with all amounts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                CurrencyGrid(
                    symbols = CurrencySymbol.entries,
                    selected = state.currencySymbol,
                    onSelect = viewModel::setCurrencySymbol,
                )

                Spacer(Modifier.height(28.dp))
                Text("Finance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Budget month starts on") },
                    supportingContent = {
                        Text(
                            if (state.budgetStartDay == 1) {
                                "1st (calendar month)"
                            } else {
                                "${budgetStartDayLabel(state.budgetStartDay)} — payday-aligned budgets"
                            },
                        )
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable { showBudgetStartDayDialog = true },
                )
                HorizontalDivider()

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

            if (showBudgetStartDayDialog) {
                BudgetStartDayDialog(
                    selected = state.budgetStartDay,
                    onSelect = {
                        viewModel.setBudgetStartDay(it)
                        showBudgetStartDayDialog = false
                    },
                    onDismiss = { showBudgetStartDayDialog = false },
                )
            }
        }
    }
}

/**
 * Preset payday choices for the budget-cycle start day (ADR-0046 amendment: the mechanism accepts
 * 1..31, but the offered choices are the common PH paydays). "End of month" stores 31, which the
 * cycle math clamps to each month's last day.
 */
private val BUDGET_START_DAY_PRESETS = listOf(
    1 to "1st (calendar month)",
    10 to "10th of the month",
    15 to "15th of the month",
    25 to "25th of the month",
    31 to "End of the month",
)

private fun budgetStartDayLabel(day: Int): String =
    BUDGET_START_DAY_PRESETS.firstOrNull { it.first == day }?.second ?: "1st (calendar month)"

/** Preset picker for the budget-cycle start day (ADR-0046). Day 1 = calendar months. */
@Composable
private fun BudgetStartDayDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget month starts on") },
        text = {
            Column {
                Text(
                    "Applies to your personal budgets, including past ones. " +
                        "Handy if you're paid mid-month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                BUDGET_START_DAY_PRESETS.forEach { (day, label) ->
                    val isSelected = day == selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                            )
                            .clickable { onSelect(day) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        if (isSelected) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
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

@Composable
private fun CurrencyGrid(
    symbols: List<CurrencySymbol>,
    selected: CurrencySymbol,
    onSelect: (CurrencySymbol) -> Unit,
) {
    val rowSize = 4
    symbols.chunked(rowSize).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) {
            row.forEach { symbol ->
                CurrencySwatch(
                    symbol = symbol,
                    isSelected = symbol == selected,
                    onClick = { onSelect(symbol) },
                    modifier = Modifier.weight(1f),
                )
            }
            // fill empty cells in the last row so swatches keep their column width
            repeat(rowSize - row.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CurrencySwatch(
    symbol: CurrencySymbol,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .then(
                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                ),
        ) {
            Text(
                symbol.glyph,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(symbol.code, style = MaterialTheme.typography.labelSmall)
    }
}

private const val PRIVACY_POLICY_URL = "https://alvinleyble.github.io/ipon-love-legal/privacy-policy.html"
