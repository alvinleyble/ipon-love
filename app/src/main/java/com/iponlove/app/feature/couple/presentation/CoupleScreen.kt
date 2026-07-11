package com.iponlove.app.feature.couple.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.partnerdebt.presentation.PartnerDebtBody
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import com.iponlove.app.feature.partnerdebt.presentation.PartnerDebtViewModel
import kotlinx.coroutines.launch

/**
 * Couple module — always reachable (2026-07-04 redesign, superseding the ADR-0024/0026
 * paired-only gating): when the user is fully paired it shows the Combined | Debts tabs; in any
 * other pairing state it renders [CoupleOverviewBody] — the same create/join (or
 * share-code-while-waiting) pairing page Settings → Couple uses — so tapping Couple while
 * unpaired lands on pairing instead of the module hiding itself from the bar.
 *
 * Two tabs, Combined | Debts, default Combined. The Debts FAB is owned here (not in
 * [PartnerDebtBody]) so the host controls the single FAB slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleScreen(
    onOpenPremium: () -> Unit = {},
    coupleViewModel: CoupleViewModel = hiltViewModel(),
    combinedViewModel: CombinedViewModel = hiltViewModel(),
    debtViewModel: PartnerDebtViewModel = hiltViewModel(),
) {
    val coupleState by coupleViewModel.state.collectAsState()
    val pairing = coupleState.pairing
    val fullyPaired = pairing is PairingState.Paired && pairing.partner != null

    if (!fullyPaired) {
        Scaffold(topBar = { TopAppBar(title = { Text("Couple") }) }) { padding ->
            CoupleOverviewBody(
                state = coupleState,
                viewModel = coupleViewModel,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
        return
    }

    val debtState by debtViewModel.uiState.collectAsState()

    val tabLabels = listOf("Combined", "Debts")
    val pagerState = rememberPagerState(pageCount = { tabLabels.size })
    val scope = rememberCoroutineScope()

    // Armed only in the fully-paired branch, where the Combined | Debts tab row exists to anchor to.
    StartTourOnFirstVisit(TutorialTours.COUPLE)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Couple") }) },
        floatingActionButton = {
            if (pagerState.currentPage == 1 && debtState.isPaired) {
                FloatingActionButton(onClick = debtViewModel::startAddDebt) {
                    Icon(Icons.Filled.Add, contentDescription = "Add debt")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.coachMarkTarget(TutorialTargets.COUPLE_TABS),
            ) {
                tabLabels.forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(label) },
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> CombinedBody(
                        viewModel = combinedViewModel,
                        onOpenPremium = onOpenPremium,
                        modifier = Modifier.fillMaxSize(),
                    )
                    1 -> PartnerDebtBody(
                        viewModel = debtViewModel,
                        onOpenPremium = onOpenPremium,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
