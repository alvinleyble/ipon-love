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
import com.iponlove.app.feature.partnerdebt.presentation.PartnerDebtBody
import com.iponlove.app.feature.partnerdebt.presentation.PartnerDebtViewModel
import kotlinx.coroutines.launch

/**
 * Couple module — paired-only (ADR-0024): reachable from the nav bar/More sheet only while
 * paired (`NavRegistry.COUPLE.requiresPaired`), so there is no unpaired state to render here
 * any more. Pairing/unpair now lives in Settings → Couple ([SettingsCoupleScreen]); the
 * dismissible Analysis-home card is the activation entry point for unpaired users.
 *
 * Two tabs, Combined | Debts, default Combined. The Debts FAB is owned here (not in
 * [PartnerDebtBody]) so the host controls the single FAB slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleScreen(
    combinedViewModel: CombinedViewModel = hiltViewModel(),
    debtViewModel: PartnerDebtViewModel = hiltViewModel(),
) {
    val debtState by debtViewModel.uiState.collectAsState()

    val tabLabels = listOf("Combined", "Debts")
    val pagerState = rememberPagerState(pageCount = { tabLabels.size })
    val scope = rememberCoroutineScope()

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
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
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
                        modifier = Modifier.fillMaxSize(),
                    )
                    1 -> PartnerDebtBody(
                        viewModel = debtViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
