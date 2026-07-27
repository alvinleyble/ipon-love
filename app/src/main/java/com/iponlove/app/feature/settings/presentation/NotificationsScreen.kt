package com.iponlove.app.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.SettingsRow
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * Notifications sub-screen (v1.6.6 Item 7). One flat row per category so far — sectioning
 * (Budgets/Recurring/Couple headers) is deferred to v1.7.1 Items 2-4, which is where the next
 * budget-alert controls land (ADR-0052/ADR-0054).
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
            SettingsRow(
                headline = "Budget alerts",
                supporting = "Notify when a budget reaches 80% or 100% of its limit. Off hides them from the inbox too.",
                index = 0,
                trailing = {
                    Switch(
                        checked = state.budgetAlertsEnabled,
                        onCheckedChange = viewModel::setBudgetAlertsEnabled,
                    )
                },
            )
            SettingsRow(
                headline = "Recurring reminders",
                supporting = "Nudge to confirm a recurring income or bill on its due date.",
                index = 1,
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
