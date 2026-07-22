package com.iponlove.app.feature.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.iponlove.app.core.ui.CurrencyGrid
import com.iponlove.app.core.ui.SettingsRow
import com.iponlove.app.core.ui.SettingsSectionHeader
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol

/**
 * Finance sub-screen (v1.6.5 Item 34): currency symbol grid (Item 18) and "Hide amounts" switch
 * (Item 15). Both write instantly — no Apply gate.
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
        }
    }
}
