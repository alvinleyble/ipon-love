package com.iponlove.app.feature.couple.presentation

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.AccentColorRow
import com.iponlove.app.feature.couple.domain.model.PairingError
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.partnerdebt.presentation.PartnerDebtBody
import com.iponlove.app.feature.partnerdebt.presentation.PartnerDebtViewModel
import kotlinx.coroutines.launch

private const val INVITE_LANDING_URL = "https://loveipon.app/invite"

/**
 * Couple module tab host (V1.4 IA consolidation — ADR-0017).
 *
 * When the user is fully paired (couple exists AND partner has accepted the invite) three tabs are
 * shown: Overview | Combined | Debts. Otherwise only the Overview tab content is rendered without
 * a tab row — the pairing entry point is always reachable regardless of state.
 *
 * The Debts FAB is owned here (not in [PartnerDebtBody]) so the host controls the single FAB slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleScreen(
    coupleViewModel: CoupleViewModel = hiltViewModel(),
    combinedViewModel: CombinedViewModel = hiltViewModel(),
    debtViewModel: PartnerDebtViewModel = hiltViewModel(),
) {
    val state by coupleViewModel.state.collectAsState()
    val debtState by debtViewModel.uiState.collectAsState()

    val paired = (state.pairing as? PairingState.Paired)?.couple
    val showTabs = paired != null && !paired.isAwaitingPartner

    val tabLabels = listOf("Overview", "Combined", "Debts")
    val pagerState = rememberPagerState(pageCount = { if (showTabs) 3 else 1 })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Couple") }) },
        floatingActionButton = {
            if (showTabs && pagerState.currentPage == 2 && debtState.isPaired) {
                FloatingActionButton(onClick = debtViewModel::startAddDebt) {
                    Icon(Icons.Filled.Add, contentDescription = "Add debt")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (showTabs) {
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    tabLabels.forEachIndexed { index, label ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(label) },
                        )
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                userScrollEnabled = showTabs,
            ) { page ->
                when (page) {
                    0 -> OverviewContent(
                        state = state,
                        viewModel = coupleViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                    1 -> CombinedBody(
                        viewModel = combinedViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                    2 -> PartnerDebtBody(
                        viewModel = debtViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewContent(
    state: CoupleUiState,
    viewModel: CoupleViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.error?.let { ErrorBanner(it) }

        when (val pairing = state.pairing) {
            PairingState.Loading ->
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

            PairingState.NotPaired -> NotPairedContent(state, viewModel)

            is PairingState.Paired ->
                PairedContent(pairing, state, viewModel, state.currentDisplayName)
        }
    }
}

@Composable
private fun NotPairedContent(state: CoupleUiState, viewModel: CoupleViewModel) {
    // Single color picker shown once — shared by both create and join flows.
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your accent color", style = MaterialTheme.typography.titleSmall)
            AccentColorRow(
                selectedHex = state.selectedColor,
                enabled = !state.isWorking,
                onSelect = viewModel::onColorSelected,
            )
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create a couple", style = MaterialTheme.typography.titleMedium)
            Text(
                "Start a shared space and send your partner the invite code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            ) { Text("Create couple") }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Join a couple", style = MaterialTheme.typography.titleMedium)
            Text(
                "Already have a code from your partner? Enter it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        }
    }
}

@Composable
private fun PairedContent(
    paired: PairingState.Paired,
    state: CoupleUiState,
    viewModel: CoupleViewModel,
    currentDisplayName: String?,
) {
    var confirmUnpair by remember { mutableStateOf(false) }
    val couple = paired.couple

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(couple.name, style = MaterialTheme.typography.titleLarge)
            val partnerLabel = when {
                couple.isAwaitingPartner -> "Waiting for your partner to join…"
                else -> "Paired with ${paired.partner?.displayName ?: "your partner"}"
            }
            Text(
                partnerLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (couple.isAwaitingPartner) {
        val context = LocalContext.current
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Invite code", style = MaterialTheme.typography.titleMedium)
                Text(
                    couple.inviteCode,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        val senderName = currentDisplayName ?: "Your partner"
                        val message = "$senderName wants to partner up on Love, Ipon to track our money together!\n\n" +
                            "Open the app and enter this invite code: ${couple.inviteCode}\n\n" +
                            "Or visit: $INVITE_LANDING_URL"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share invite"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Share invite code")
                }
                Text(
                    "Share this code with your partner so they can join.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = viewModel::rotateInviteCode,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Generate a new code") }
            }
        }
    }

    OutlinedButton(
        onClick = { confirmUnpair = true },
        enabled = !state.isWorking,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Unpair") }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair?") },
            text = {
                Text(
                    "This dissolves the couple for both of you. Shared budgets are removed " +
                        "and shared notes revert to their owner. You keep all your own data.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    viewModel.unpair()
                }) { Text("Unpair") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ErrorBanner(error: PairingError) {
    Card(Modifier.fillMaxWidth()) {
        Text(
            text = error.message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun PairingError.message(): String = when (this) {
    PairingError.ALREADY_IN_COUPLE -> "You're already in a couple."
    PairingError.INVALID_INVITE_CODE -> "That invite code isn't valid."
    PairingError.COUPLE_FULL -> "That couple already has two members."
    PairingError.OWN_COUPLE -> "You can't join your own couple."
    PairingError.NOT_IN_COUPLE -> "You're not in a couple."
    PairingError.NETWORK -> "No connection. Pairing needs to be online — try again."
    PairingError.UNKNOWN -> "Something went wrong. Please try again."
}
