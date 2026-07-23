package com.iponlove.app.feature.settings.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.BuildConfig
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.SettingsRow
import com.iponlove.app.core.ui.SettingsSectionHeader
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.relativeTimeLabel
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizeScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit = {},
    onOpenFinance: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenPremium: (source: String) -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenCouple: () -> Unit = {},
    onOpenNavbar: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenBetaFeedback: () -> Unit = {},
    onOpenUpcomingFeatures: () -> Unit = {},
    onReplayTutorial: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: PersonalizeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colors = LocalPlayfulColors.current

    // Couple-scoped Settings tour — no-ops until paired (ADR-0038); anchors to the Couple entry.
    StartTourOnFirstVisit(TutorialTours.COUPLE_SETTINGS)

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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Sync first (Item 34): the freshness/"Sync now" card is the most-checked control.
            SettingsSectionHeader("Sync")
            SyncCard(
                lastSyncedAt = state.lastSyncedAt,
                isSyncing = state.isSyncing,
                syncFailed = state.syncFailed,
                isOnline = state.isOnline,
                onSyncNow = viewModel::syncNow,
            )

            Spacer(Modifier.height(14.dp))
            SettingsSectionHeader("Account")
            SettingsRow(
                headline = "Profile",
                supporting = "Display name, color & account",
                index = 0,
                onClick = onOpenProfile,
            )
            // Dormant until enforcement flips ON (paywall S5 / Item 12): the row is absent
            // entirely while the paywall ships dormant, so a normal walkthrough shows nothing.
            if (state.showPremiumEntry) {
                SettingsRow(
                    headline = "Premium",
                    supporting = if (state.isPremium) "Active — thank you!" else "Unlock more of Love, Ipon",
                    index = 1,
                    onClick = { onOpenPremium("settings") },
                )
            }
            SettingsRow(
                headline = "Security",
                supporting = "PIN lock & biometric",
                index = 2,
                onClick = onOpenSecurity,
            )
            SettingsRow(
                headline = "Notifications",
                supporting = "Budget alerts",
                index = 3,
                onClick = onOpenNotifications,
            )

            Spacer(Modifier.height(14.dp))
            SettingsSectionHeader("Personalize")
            SettingsRow(
                headline = "Appearance",
                supporting = "Theme, colors & dark mode",
                index = 0,
                onClick = onOpenAppearance,
            )
            SettingsRow(
                headline = "Edit navbar",
                supporting = "Choose & reorder bottom-bar shortcuts",
                index = 1,
                onClick = onOpenNavbar,
            )

            Spacer(Modifier.height(14.dp))
            SettingsSectionHeader("Finance")
            SettingsRow(
                headline = "Finance",
                supporting = "Currency & privacy",
                index = 0,
                onClick = onOpenFinance,
            )

            Spacer(Modifier.height(14.dp))
            SettingsSectionHeader("Couple")
            SettingsRow(
                headline = "Couple",
                supporting = "Pairing, invite code & unpair",
                index = 0,
                onClick = onOpenCouple,
                modifier = Modifier.coachMarkTarget(TutorialTargets.SETTINGS_COUPLE),
            )

            Spacer(Modifier.height(14.dp))
            SettingsSectionHeader("Support")
            SettingsRow(
                headline = "Help",
                supporting = "FAQs and app guide",
                index = 0,
                onClick = onOpenHelp,
            )
            SettingsRow(
                headline = "Replay tutorial",
                supporting = "Take the quick app walkthrough again",
                index = 1,
                onClick = onReplayTutorial,
            )
            SettingsRow(
                headline = "Privacy Policy",
                index = 2,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)),
                    )
                },
            )
            SettingsRow(
                headline = "About",
                supporting = "Version, licenses, rate & share",
                index = 3,
                onClick = onOpenAbout,
            )

            if (BuildConfig.IS_BETA_BUILD) {
                Spacer(Modifier.height(14.dp))
                SettingsSectionHeader("Beta")
                SettingsRow(
                    headline = "Beta feedback",
                    supporting = "Report bugs or suggest improvements",
                    index = 0,
                    onClick = onOpenBetaFeedback,
                )
                SettingsRow(
                    headline = "Upcoming features",
                    supporting = "See what's on our roadmap",
                    index = 1,
                    onClick = onOpenUpcomingFeatures,
                )
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

/**
 * The Settings sync status card (v1.6.5 Item 9): last-synced line + status line + "Sync now".
 * Friendly copy only — the raw failure cause goes to logcat in the engine, never here.
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 6g): a glass [PlayfulCard] wrapper; the "Sync
 * now" button stays a plain M3 [Button] per the "functional controls stay conservative" convention.
 */
@Composable
private fun SyncCard(
    lastSyncedAt: Instant?,
    isSyncing: Boolean,
    syncFailed: Boolean,
    isOnline: Boolean,
    onSyncNow: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (lastSyncedAt != null) "Last synced ${relativeTimeLabel(lastSyncedAt)}"
                    else "Not synced yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                // One status line, priority-ordered: an in-flight sync beats everything, offline
                // beats a stale error (reconnecting is the fix), and Error is transient (Item 9).
                val statusLine = when {
                    isSyncing -> "Syncing…"
                    !isOnline -> "Offline — changes sync when you reconnect"
                    syncFailed -> "Couldn't sync — try again"
                    else -> null
                }
                if (statusLine != null) {
                    Text(
                        statusLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (syncFailed && !isSyncing && isOnline) colors.semantic.negative
                        else colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onSyncNow,
                enabled = isOnline && !isSyncing,
            ) {
                Text(if (isSyncing) "Syncing…" else "Sync now")
            }
        }
    }
}

private const val PRIVACY_POLICY_URL = "https://alvinleyble.github.io/ipon-love-legal/privacy-policy.html"
