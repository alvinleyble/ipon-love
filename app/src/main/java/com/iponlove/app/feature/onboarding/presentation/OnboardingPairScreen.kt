package com.iponlove.app.feature.onboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.AccentColorRow
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.presentation.CoupleViewModel
import com.iponlove.app.feature.couple.presentation.ErrorBanner
import com.iponlove.app.feature.couple.presentation.PairedContent

private enum class PairChoice { NONE, INVITE, CODE }

/**
 * Onboarding step 3/4 — the explicit pair-or-solo fork (ADR-0024): "Invite my partner"
 * ([CoupleViewModel.createCouple]) vs. "I have a code" ([CoupleViewModel.redeemInvite]) vs.
 * Solo. Reuses [CoupleViewModel] (same use cases as Settings → Couple) so two partners both
 * onboarding still converge on one couple, but presents a progressive fork instead of both
 * forms at once — Settings' management page is the always-both-visible variant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingPairScreen(
    onContinue: () -> Unit,
    viewModel: CoupleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalPlayfulColors.current
    var choice by rememberSaveable { mutableStateOf(PairChoice.NONE) }
    val paired = state.pairing as? PairingState.Paired

    Box(modifier = Modifier.fillMaxSize().playfulBackground()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = colors.textPrimary,
                    ),
                    title = { Text("Team up?") },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.error?.let { ErrorBanner(it) }

                when {
                    paired != null -> {
                        Text(
                            "You're set — pairing complete.",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        PairedContent(
                            paired = paired,
                            state = state,
                            viewModel = viewModel,
                            currentDisplayName = state.currentDisplayName,
                        )
                        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                            Text("Continue")
                        }
                    }

                    choice == PairChoice.NONE -> {
                        Text(
                            "Track money together?",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                        )
                        Text(
                            "Pair with your partner for a combined view, shared budgets, and an IOU " +
                                "tracker. You can always do this later from Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        Button(
                            onClick = { choice = PairChoice.INVITE },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Invite my partner") }
                        OutlinedButton(
                            onClick = { choice = PairChoice.CODE },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("I have a code") }
                        TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                            Text("Skip — I'll go solo")
                        }
                    }

                    choice == PairChoice.INVITE -> {
                        Text(
                            "Create a couple",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        AccentColorRow(
                            selectedHex = state.selectedColor,
                            enabled = !state.isWorking,
                            onSelect = viewModel::onColorSelected,
                        )
                        OutlinedTextField(
                            value = state.nameInput,
                            onValueChange = viewModel::onNameChange,
                            label = { Text("Couple name") },
                            singleLine = true,
                            enabled = !state.isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = viewModel::createCouple,
                            enabled = state.canCreate,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Send invite") }
                        TextButton(onClick = { choice = PairChoice.NONE }) { Text("Back") }
                    }

                    choice == PairChoice.CODE -> {
                        Text(
                            "Join a couple",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        AccentColorRow(
                            selectedHex = state.selectedColor,
                            enabled = !state.isWorking,
                            onSelect = viewModel::onColorSelected,
                        )
                        OutlinedTextField(
                            value = state.codeInput,
                            onValueChange = viewModel::onCodeChange,
                            label = { Text("Invite code") },
                            singleLine = true,
                            enabled = !state.isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = viewModel::redeemInvite,
                            enabled = state.canRedeem,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Join couple") }
                        TextButton(onClick = { choice = PairChoice.NONE }) { Text("Back") }
                    }
                }
            }
        }
    }
}
