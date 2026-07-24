package com.iponlove.app.feature.settings.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.couple.presentation.CoupleOverviewBody
import com.iponlove.app.feature.couple.presentation.CoupleViewModel

/**
 * Pairing/unpair, relocated here from the Couple module's old Overview tab (ADR-0024). The
 * Couple module itself ([com.iponlove.app.feature.couple.presentation.CoupleScreen]) is now
 * paired-only, so this is the sole reachable pairing entry point besides the Analysis-home
 * pairing card.
 *
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 6g): the Scaffold chrome (transparent container +
 * retinted top bar) shows the app-wide Playful backdrop through. The shared [CoupleOverviewBody]
 * pairing/unpair body is deliberately deferred to Slice 6h with the rest of onboarding/pairing (it
 * is also used by the un-restyled `CoupleScreen` unpaired branch, per Slice 6e's boundary).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCoupleScreen(
    onBack: () -> Unit,
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: CoupleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
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
                title = { Text("Couple") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        CoupleOverviewBody(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.padding(padding).fillMaxSize(),
            onOpenPremium = onOpenPremium,
        )
    }
}
