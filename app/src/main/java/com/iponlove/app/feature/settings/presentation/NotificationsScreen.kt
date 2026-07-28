package com.iponlove.app.feature.settings.presentation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.SettingsRow
import com.iponlove.app.core.ui.SettingsSectionHeader
import com.iponlove.app.core.ui.onPlayfulSurface
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * Notifications sub-screen. Sectioned since v1.7.1 Items 2-4 (ADR-0054): a "Budgets" section
 * groups the master switch with the two configurable rungs; Recurring reminders (Item 1) stays
 * its own flat row outside any section (its own grill decided it doesn't warrant one). A "Couple"
 * section (Item 9) holds the sole partner-debt-alert switch and hides entirely while unpaired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalPlayfulColors.current
    val context = LocalContext.current

    // Re-read on resume, not once at init: tapping the banner leaves the app for system settings,
    // and the screen is still composed when the user returns (ADR-0056 decision 9).
    LifecycleResumeEffect(Unit) {
        viewModel.refreshNotificationPermission()
        onPauseOrDispose { }
    }

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
                // Scrollable since v1.7.1 Item 12 (matching PersonalizeScreen): the screen was one
                // row short of overflowing already, and this slice adds a section header, a sub-row
                // and a conditional banner — without this the Couple switch falls off the bottom.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.notificationsBlocked) {
                NotificationsBlockedBanner(onOpenSystemSettings = { context.openAppNotificationSettings() })
            }
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
            // The master row sat header-less between Budgets and Couple until v1.7.1 Item 12, so it
            // read as a Budgets control — a pre-existing wart that hanging a sub-row off it would
            // have compounded (ADR-0056 decision 7).
            SettingsSectionHeader("Recurring")
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
            NestedSwitchRow(
                index = 5,
                headline = "Remind me when the app is closed",
                // Hedged deliberately (ADR-0056 decision 7): App Standby throttles this hardest for
                // the very user it targets, and force-stopping ROMs drop it entirely. This copy is
                // the only thing standing between a working implementation and a bug report — it
                // must never harden into a clock-time promise.
                supporting = "Checks a few times a day. Some phones limit background activity, " +
                    "so reminders may be delayed.",
                checked = state.offAppRecurringRemindersEnabled,
                onCheckedChange = viewModel::setOffAppRecurringRemindersEnabled,
                enabled = state.recurringRemindersEnabled,
            )
            // Hidden entirely while unpaired (Item 9 grill) — the Debt Tracker itself is
            // paired-only, so a mute for it makes no sense to show before pairing.
            if (state.isPaired) {
                SettingsSectionHeader("Couple")
                SettingsRow(
                    headline = "Partner debt alerts",
                    supporting = "Notify me when my partner logs a new debt I owe.",
                    index = 6,
                    trailing = {
                        Switch(
                            checked = state.partnerDebtAlertsEnabled,
                            onCheckedChange = viewModel::setPartnerDebtAlertsEnabled,
                        )
                    },
                )
            }
        }
    }
}

/**
 * Screen-level warning that OS notifications are blocked (ADR-0056 decision 9). Every switch below
 * reads "on" while `notify()` silently does nothing — tolerable for the categories you'd find next
 * time you opened the app anyway, but the off-app reminder exists *only* to reach a user who isn't
 * opening it, so without this banner it is a permanent no-op with no signal. App-level check only;
 * per-channel importance would double the surface for a rare state.
 */
@Composable
private fun NotificationsBlockedBanner(onOpenSystemSettings: () -> Unit) {
    // Blush, not Glass: this must read as an alert against the rows below. Ink comes from
    // onPlayfulSurface, never a hardcoded textPrimary — in dark mode blush is near-white, which is
    // exactly how Item 6's unread inbox rows shipped invisible.
    val ink = onPlayfulSurface(PlayfulSurface.Blush)
    PlayfulCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSystemSettings),
        surface = PlayfulSurface.Blush,
        shape = LeafShapes.leafFor(0, 22.dp, 9.dp),
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsOff,
                contentDescription = null,
                tint = ink,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notifications are turned off",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                )
                Text(
                    text = "Love, Ipon can't reach you until you allow notifications in your phone's settings. Tap to open them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ink.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/**
 * A [SettingsRow]-shaped switch row that reads as subordinate to the row above it: indented, and
 * greyed out (visible, inactive) rather than hidden when [enabled] is false — the shape ADR-0054
 * decision 9 established for the Budgets sliders, so the stored value survives the master going off.
 */
@Composable
private fun NestedSwitchRow(
    index: Int,
    headline: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) colors.textPrimary else colors.textTertiary,
                )
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) colors.textSecondary else colors.textTertiary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/** Deep-link straight to this app's notification settings (API 26+, and minSdk is 26). */
private fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // A ROM without the screen would otherwise crash Settings rather than the missing permission.
    runCatching { startActivity(intent) }
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
