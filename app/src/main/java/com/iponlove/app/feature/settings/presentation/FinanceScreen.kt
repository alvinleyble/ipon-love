package com.iponlove.app.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.CurrencyGrid
import com.iponlove.app.core.ui.SettingsRow
import com.iponlove.app.core.ui.SettingsSectionHeader
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol

/**
 * Finance sub-screen (v1.6.5 Item 34): currency symbol grid (Item 18), "Hide amounts" switch
 * (Item 15), and the budget-cycle start day (Item 10b). All three write instantly — no Apply gate.
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 6g).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    onBack: () -> Unit,
    viewModel: FinanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalPlayfulColors.current
    var showBudgetStartDayDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                    actionIconContentColor = colors.textSecondary,
                ),
                title = { Text("Finance") },
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            SettingsSectionHeader("Currency")
            Spacer(Modifier.height(4.dp))
            Text(
                "Symbol shown with all amounts",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            CurrencyGrid(
                symbols = CurrencySymbol.entries,
                selected = state.currencySymbol,
                onSelect = viewModel::setCurrencySymbol,
            )

            Spacer(Modifier.height(24.dp))
            SettingsSectionHeader("Privacy")
            Spacer(Modifier.height(10.dp))
            SettingsRow(
                headline = "Hide amounts",
                supporting = "Mask money values on screen",
                trailing = {
                    Switch(
                        checked = state.privacyModeEnabled,
                        onCheckedChange = viewModel::setPrivacyMode,
                    )
                },
            )

            Spacer(Modifier.height(24.dp))
            SettingsSectionHeader("Budgets")
            Spacer(Modifier.height(10.dp))
            SettingsRow(
                headline = "Budget month starts on",
                supporting = if (state.budgetStartDay == 1) {
                    "1st (calendar month)"
                } else {
                    "${budgetStartDayLabel(state.budgetStartDay)} — payday-aligned budgets"
                },
                onClick = { showBudgetStartDayDialog = true },
            )
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

/**
 * Preset picker for the budget-cycle start day (ADR-0046). Day 1 = calendar months. A one-off
 * simple dialog — keeps the conservative M3 container per the Slice-6 rollout's standing exception.
 */
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
