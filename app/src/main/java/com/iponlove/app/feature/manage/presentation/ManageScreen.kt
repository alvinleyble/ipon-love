package com.iponlove.app.feature.manage.presentation

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
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
 * screens (V1.4 IA consolidation — ADR-0017). Owns one [Scaffold] + [PrimaryTabRow]/[HorizontalPager]
 * (the reusable Analysis tab pattern) and one page-aware FAB that delegates the add action to the
 * active tab's ViewModel. Each tab body keeps its own ViewModel + editor dialog; the bodies are
 * chrome-less so this host provides the only top bar and FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    accountsViewModel: AccountsViewModel = hiltViewModel(),
    categoriesViewModel: CategoriesViewModel = hiltViewModel(),
    budgetsViewModel: BudgetsViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val tabLabels = listOf("Accounts", "Categories", "Budgets")

    StartTourOnFirstVisit(TutorialTours.MANAGE)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Manage") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (pagerState.currentPage) {
                        0 -> accountsViewModel.startCreate()
                        1 -> categoriesViewModel.startCreate()
                        2 -> budgetsViewModel.startCreate()
                    }
                },
                modifier = Modifier.coachMarkTarget(TutorialTargets.MANAGE_ADD),
            ) {
                val description = when (pagerState.currentPage) {
                    0 -> "Add account"
                    1 -> "Add category"
                    else -> "Add budget"
                }
                Icon(Icons.Filled.Add, contentDescription = description)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.coachMarkTarget(TutorialTargets.MANAGE_TABS),
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
                    0 -> AccountsBody(viewModel = accountsViewModel, modifier = Modifier.fillMaxSize())
                    1 -> CategoriesBody(viewModel = categoriesViewModel, modifier = Modifier.fillMaxSize())
                    2 -> BudgetsBody(viewModel = budgetsViewModel, modifier = Modifier.fillMaxSize())
                    else -> {}
                }
            }
        }
    }
}
