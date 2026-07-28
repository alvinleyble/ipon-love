package com.iponlove.app.feature.couple.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.PlayfulChip
import com.iponlove.app.core.ui.PlayfulScreenTitle
import com.iponlove.app.core.ui.PrivacyEyeAction
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
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
 *
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 6e) — Debts is the last Couple tab, so this slice
 * folds in the previously-orphaned host chrome (deferred by Slice 4 "until all tabs restyled"): a
 * transparent-container [Scaffold] with a tilted [PlayfulScreenTitle], the Combined/Debts switcher
 * as leaf-pill [PlayfulChip]s (Slice 1's Analysis tab pattern), and a −4° accent squircle FAB. The
 * unpaired pairing branch below is left on M3 chrome — it restyles with onboarding/pairing (6h).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleScreen(
    onOpenPremium: (source: String) -> Unit = {},
    coupleViewModel: CoupleViewModel = hiltViewModel(),
    combinedViewModel: CombinedViewModel = hiltViewModel(),
    debtViewModel: PartnerDebtViewModel = hiltViewModel(),
) {
    val coupleState by coupleViewModel.state.collectAsState()
    val pairing = coupleState.pairing
    val fullyPaired = pairing is PairingState.Paired && pairing.partner != null

    if (!fullyPaired) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(Modifier.statusBarsPadding().padding(top = 10.dp, bottom = 2.dp)) {
                    PlayfulScreenTitle(title = "Couple", actions = { PrivacyEyeAction() })
                }
            },
        ) { padding ->
            CoupleOverviewBody(
                state = coupleState,
                viewModel = coupleViewModel,
                modifier = Modifier.padding(padding).fillMaxSize().playfulBackground(),
                onOpenPremium = onOpenPremium,
            )
        }
        return
    }

    val debtState by debtViewModel.uiState.collectAsState()

    val tabLabels = listOf("Spending", "Debts")
    val pagerState = rememberPagerState(pageCount = { tabLabels.size })
    val scope = rememberCoroutineScope()

    // Armed only in the fully-paired branch, where the Combined | Debts tab row exists to anchor to.
    StartTourOnFirstVisit(TutorialTours.COUPLE)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(Modifier.statusBarsPadding().padding(top = 10.dp, bottom = 2.dp)) {
                PlayfulScreenTitle(title = "Couple", actions = { PrivacyEyeAction() })
            }
        },
        floatingActionButton = {
            if (pagerState.currentPage == 1 && debtState.isPaired) {
                CoupleFab(onClick = debtViewModel::startAddDebt, description = "Add debt")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().playfulBackground()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp)
                    .coachMarkTarget(TutorialTargets.COUPLE_TABS),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabLabels.forEachIndexed { index, label ->
                    PlayfulChip(
                        label = label,
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        modifier = Modifier.weight(1f),
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

/** The −4° squircle FAB, matching the global add-transaction FAB's identity language
 *  (the Records/Savings/Manage `Fab` recipe). */
@Composable
private fun CoupleFab(onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .rotate(-4f)
            .size(56.dp)
            .clip(LeafShapes.Fab)
            .background(colors.accent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = description,
            tint = colors.onAccent,
            modifier = Modifier.size(27.dp).rotate(4f),
        )
    }
}
