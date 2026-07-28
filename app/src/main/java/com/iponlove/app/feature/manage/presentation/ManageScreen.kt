package com.iponlove.app.feature.manage.presentation

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
import androidx.compose.runtime.Composable
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
import com.iponlove.app.feature.accounts.presentation.AccountsBody
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import com.iponlove.app.feature.accounts.presentation.AccountsViewModel
import com.iponlove.app.feature.budgets.presentation.BudgetsBody
import com.iponlove.app.feature.budgets.presentation.BudgetsViewModel
import com.iponlove.app.feature.categories.presentation.CategoriesBody
import com.iponlove.app.feature.categories.presentation.CategoriesViewModel
import kotlinx.coroutines.launch

/**
 * Manage module: a single tab host over the formerly-standalone Accounts, Categories, and Budgets
 * screens (V1.4 IA consolidation — ADR-0017). Owns one [Scaffold] + a leaf-pill tab switcher/
 * [HorizontalPager] (the reusable Analysis tab pattern) and one page-aware FAB that delegates the
 * add action to the active tab's ViewModel. Each tab body keeps its own ViewModel + editor dialog;
 * the bodies are chrome-less so this host provides the only top bar and FAB.
 *
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 6c) — the last of the three Manage tabs to land,
 * so this slice also folds in the previously-orphaned host chrome (deferred by Slices 3/6b "until
 * all tabs restyled"): a transparent-container [Scaffold] with a tilted [PlayfulScreenTitle], the
 * tab row as leaf-pill [PlayfulChip]s (matching Slice 1's Analysis tab pattern), and a −4° accent
 * squircle FAB (the Records/Savings `Fab` recipe) replacing the plain M3 one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    onOpenPremium: (source: String) -> Unit = {},
    accountsViewModel: AccountsViewModel = hiltViewModel(),
    categoriesViewModel: CategoriesViewModel = hiltViewModel(),
    budgetsViewModel: BudgetsViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val tabLabels = listOf("Accounts", "Categories", "Budgets")

    StartTourOnFirstVisit(TutorialTours.MANAGE)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(Modifier.statusBarsPadding().padding(top = 10.dp, bottom = 2.dp)) {
                PlayfulScreenTitle(title = "Manage", actions = { PrivacyEyeAction() })
            }
        },
        floatingActionButton = {
            ManageFab(
                onClick = {
                    when (pagerState.currentPage) {
                        0 -> accountsViewModel.startCreate()
                        1 -> categoriesViewModel.startCreate()
                        2 -> budgetsViewModel.startCreate()
                    }
                },
                description = when (pagerState.currentPage) {
                    0 -> "Add account"
                    1 -> "Add category"
                    else -> "Add budget"
                },
                modifier = Modifier.coachMarkTarget(TutorialTargets.MANAGE_ADD),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().playfulBackground()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp)
                    .coachMarkTarget(TutorialTargets.MANAGE_TABS),
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
                    0 -> AccountsBody(
                        viewModel = accountsViewModel,
                        onOpenPremium = onOpenPremium,
                        modifier = Modifier.fillMaxSize(),
                    )
                    1 -> CategoriesBody(
                        viewModel = categoriesViewModel,
                        onOpenPremium = onOpenPremium,
                        modifier = Modifier.fillMaxSize(),
                    )
                    2 -> BudgetsBody(
                        viewModel = budgetsViewModel,
                        onOpenPremium = onOpenPremium,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> {}
                }
            }
        }
    }
}

/** The center −4° squircle FAB, matching the global add-transaction FAB's identity language
 *  (Savings' `SavingsFab` / Records' `RecordsFab` recipe). */
@Composable
private fun ManageFab(onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
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
