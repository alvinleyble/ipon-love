package com.iponlove.app.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.SettingsRow
import com.iponlove.app.core.ui.SettingsSectionHeader
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * Notifications sub-screen. Sectioned since v1.7.1 Items 2-4 (ADR-0054): a "Budgets" section
 * groups the master switch with the two configurable rungs; Recurring reminders (Item 1) stays
 * its own flat row outside any section (its own grill decided it doesn't warrant one).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalPlayfulColors.current

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
                title = { Text("Notifications") },
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSectionHeader("Budgets")
            SettingsRow(
                headline = "Budget alerts",
                supporting = "Notify when a budget crosses your warn or limit threshold. Off hides them from the inbox too.",
                index = 0,
                trailing = {
                    Switch(
                        checked = state.budgetAlertsEnabled,
                        onCheckedChange = viewModel::setBudgetAlertsEnabled,
                    )
                },
            )
            BudgetThresholdSliderCard(
                index = 1,
                label = "Warn me at",
                percent = state.budgetWarnThresholdPercent,
                onPercentChange = viewModel::setBudgetWarnThreshold,
                valueRange = 5f..100f,
                stepSize = 5,
                enabled = state.budgetAlertsEnabled,
                supporting = "A gentle heads-up before you hit your budget's limit.",
            )
            SettingsRow(
                headline = "Way over budget alert",
                supporting = "A second, one-time alert when a budget is badly exceeded.",
                index = 2,
                trailing = {
                    Switch(
                        checked = state.budgetOverAlertsEnabled,
                        onCheckedChange = viewModel::setBudgetOverAlertsEnabled,
                        enabled = state.budgetAlertsEnabled,
                    )
                },
            )
            BudgetThresholdSliderCard(
                index = 3,
                label = "Way over at",
                percent = state.budgetOverThresholdPercent,
                onPercentChange = viewModel::setBudgetOverThreshold,
                valueRange = 110f..300f,
                stepSize = 10,
                enabled = state.budgetAlertsEnabled && state.budgetOverAlertsEnabled,
                supporting = "Fires once per month per budget — not repeated nagging.",
            )
            SettingsRow(
                headline = "Recurring reminders",
                supporting = "Nudge to confirm a recurring income or bill on its due date.",
                index = 4,
                trailing = {
                    Switch(
                        checked = state.recurringRemindersEnabled,
                        onCheckedChange = viewModel::setRecurringRemindersEnabled,
                    )
                },
            )
        }
    }
}

/** One rung's slider — value label + [Slider], greyed out (visible, inactive) when [enabled] is false. */
@Composable
private fun BudgetThresholdSliderCard(
    index: Int,
    label: String,
    percent: Int,
    onPercentChange: (Int) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Int,
    enabled: Boolean,
    supporting: String,
) {
    val colors = LocalPlayfulColors.current
    val steps = ((valueRange.endInclusive - valueRange.start) / stepSize).toInt() - 1
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
        contentPadding = 16.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) colors.textPrimary else colors.textTertiary,
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) colors.accent else colors.textTertiary,
                )
            }
            Slider(
                value = percent.toFloat(),
                onValueChange = { raw ->
                    // Slider's onValueChange delivers the raw continuous drag/tap position —
                    // `steps` only snaps the rendered thumb, not this callback's value. Snap here
                    // so the label and stored threshold always land on a stepSize multiple.
                    val snapped = (Math.round((raw - valueRange.start) / stepSize) * stepSize + valueRange.start)
                        .toInt()
                        .coerceIn(valueRange.start.toInt(), valueRange.endInclusive.toInt())
                    onPercentChange(snapped)
                },
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
