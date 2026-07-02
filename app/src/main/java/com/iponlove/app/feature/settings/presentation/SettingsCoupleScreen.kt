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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.feature.couple.presentation.CoupleOverviewBody
import com.iponlove.app.feature.couple.presentation.CoupleViewModel

/**
 * Pairing/unpair, relocated here from the Couple module's old Overview tab (ADR-0024). The
 * Couple module itself ([com.iponlove.app.feature.couple.presentation.CoupleScreen]) is now
 * paired-only, so this is the sole reachable pairing entry point besides the Analysis-home
 * pairing card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCoupleScreen(
    onBack: () -> Unit,
    viewModel: CoupleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
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
        )
    }
}
