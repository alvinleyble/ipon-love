package com.iponlove.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iponlove.app.feature.accounts.presentation.AccountsScreen
import com.iponlove.app.feature.analysis.presentation.AnalysisScreen
import com.iponlove.app.feature.budgets.presentation.BudgetsScreen
import com.iponlove.app.feature.categories.presentation.CategoriesScreen
import com.iponlove.app.feature.transactions.presentation.TransactionsScreen

/**
 * App root: a bottom-nav [Scaffold] hosting one composable per [TopLevelDestination].
 * Each feature screen brings its own top bar/FAB, so this only owns the bottom bar and
 * the [NavHost].
 */
@Composable
fun IponApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Single instance per tab; preserve each tab's scroll/state.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.RECORDS.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.RECORDS.route) { TransactionsScreen() }
            composable(TopLevelDestination.ANALYSIS.route) { AnalysisScreen() }
            composable(TopLevelDestination.BUDGETS.route) { BudgetsScreen() }
            composable(TopLevelDestination.ACCOUNTS.route) { AccountsScreen() }
            composable(TopLevelDestination.CATEGORIES.route) { CategoriesScreen() }
        }
    }
}
