package com.iponlove.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iponlove.app.feature.accounts.presentation.AccountsScreen
import com.iponlove.app.feature.analysis.presentation.AnalysisScreen
import com.iponlove.app.feature.applock.presentation.AppLockSetupScreen
import com.iponlove.app.feature.budgets.presentation.BudgetsScreen
import com.iponlove.app.feature.categories.presentation.CategoriesScreen
import com.iponlove.app.feature.couple.presentation.CombinedScreen
import com.iponlove.app.feature.couple.presentation.CoupleScreen
import com.iponlove.app.feature.notes.presentation.NoteEditorScreen
import com.iponlove.app.feature.notes.presentation.NoteEditorViewModel.Companion.NOTE_ID_KEY
import com.iponlove.app.feature.notes.presentation.NotesScreen
import com.iponlove.app.feature.partnerdebt.presentation.PartnerDebtScreen
import com.iponlove.app.feature.recurring.presentation.RecurringScreen
import com.iponlove.app.feature.settings.presentation.PersonalizeScreen
import com.iponlove.app.feature.settings.presentation.ProfileScreen
import com.iponlove.app.feature.transactions.presentation.TransactionsScreen
import kotlinx.coroutines.launch

private const val APP_LOCK_SETUP_ROUTE = "app_lock_setup"
private const val NOTE_EDITOR_ROUTE = "note_editor"
private const val PROFILE_ROUTE = "profile"
private const val NAV_EDITOR_ROUTE = "nav_editor"

/**
 * App root: a bottom-nav [Scaffold] whose bar is built dynamically from the user's pinned
 * [NavConfig] (ADR-0017) — up to [NavRegistry.MAX_PINS] reorderable pins plus an always-present
 * "More". The [NavHost] declares *every* registry destination so `saveState`/`restoreState`
 * work across the dynamic pin set; pinning only changes which destinations the bar surfaces.
 */
@Composable
fun IponApp(
    onSignOut: () -> Unit,
    navViewModel: NavbarViewModel = hiltViewModel(),
) {
    val state by navViewModel.uiState.collectAsState()

    if (!state.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    IponAppContent(state = state, onSignOut = onSignOut, navViewModel = navViewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IponAppContent(
    state: NavUiState,
    onSignOut: () -> Unit,
    navViewModel: NavbarViewModel,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // NavHost can't swap its start without rebuilding the graph (wiping the back stack), so the
    // home destination is captured once. Reordering pins later updates the bar, not home.
    val startRoute = rememberSaveable { state.startRoute }

    var showMore by rememberSaveable { mutableStateOf(false) }

    val visiblePins = state.visiblePinIds.mapNotNull { NavRegistry.byId[it] }
        .ifEmpty { listOf(NavRegistry.RECORDS) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                visiblePins.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = { navController.switchTab(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
                val onModuleRoute = NavRegistry.all.any { it.route == currentRoute }
                NavigationBarItem(
                    selected = onModuleRoute && visiblePins.none { it.route == currentRoute },
                    onClick = { showMore = true },
                    icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = "More") },
                    label = { Text("More") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable(NavRegistry.RECORDS.route) {
                TransactionsScreen(
                    onOpenRecurring = { navController.navigate(NavRegistry.RECURRING.route) },
                    onOpenNotes = { navController.navigate(NavRegistry.NOTES.route) },
                    onOpenCouple = { navController.navigate(NavRegistry.COUPLE.route) },
                    onOpenPersonalize = { navController.navigate(NavRegistry.SETTINGS.route) },
                    onSignOut = onSignOut,
                )
            }
            composable(NavRegistry.ANALYSIS.route) { AnalysisScreen() }
            composable(NavRegistry.BUDGETS.route) { BudgetsScreen() }
            composable(NavRegistry.ACCOUNTS.route) { AccountsScreen() }
            composable(NavRegistry.CATEGORIES.route) { CategoriesScreen() }
            composable(NavRegistry.NOTES.route) {
                NotesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { noteId -> navController.navigate("$NOTE_EDITOR_ROUTE/$noteId") },
                )
            }
            composable(NavRegistry.RECURRING.route) {
                RecurringScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRegistry.COUPLE.route) {
                CoupleScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCombined = { navController.navigate(NavRegistry.COMBINED.route) },
                    onOpenDebts = { navController.navigate(NavRegistry.PARTNER_DEBT.route) },
                )
            }
            composable(NavRegistry.COMBINED.route) {
                CombinedScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRegistry.PARTNER_DEBT.route) {
                PartnerDebtScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRegistry.SETTINGS.route) {
                PersonalizeScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { navController.navigate(PROFILE_ROUTE) },
                    onOpenSecurity = { navController.navigate(APP_LOCK_SETUP_ROUTE) },
                    onOpenNavbar = { navController.navigate(NAV_EDITOR_ROUTE) },
                )
            }
            composable(PROFILE_ROUTE) {
                ProfileScreen(onBack = { navController.popBackStack() })
            }
            composable(APP_LOCK_SETUP_ROUTE) {
                AppLockSetupScreen(onBack = { navController.popBackStack() })
            }
            composable(NAV_EDITOR_ROUTE) {
                NavbarEditorScreen(
                    initialConfig = state.config,
                    isPaired = state.isPaired,
                    onApply = navViewModel::applyConfig,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "$NOTE_EDITOR_ROUTE/{$NOTE_ID_KEY}",
                arguments = listOf(navArgument(NOTE_ID_KEY) { type = NavType.StringType }),
            ) {
                NoteEditorScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    if (showMore) {
        MoreSheet(
            modules = state.visibleModuleIds.mapNotNull { NavRegistry.byId[it] },
            onModule = { dest ->
                showMore = false
                navController.switchTab(dest.route)
            },
            onEditNavbar = {
                showMore = false
                navController.navigate(NAV_EDITOR_ROUTE)
            },
            onDismiss = { showMore = false },
        )
    }
}

/** Top-level tab switch: single instance per destination, each tab's state saved/restored. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    modules: List<NavDestination>,
    onModule: (NavDestination) -> Unit,
    onEditNavbar: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun dismissThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) action()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "All modules",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
            val columns = 4
            modules.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    row.forEach { dest ->
                        ModuleCell(
                            dest = dest,
                            onClick = { dismissThen { onModule(dest) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Edit navbar") },
                supportingContent = { Text("Choose & reorder your shortcuts") },
                leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                modifier = Modifier.clickable { dismissThen(onEditNavbar) },
            )
        }
    }
}

@Composable
private fun ModuleCell(
    dest: NavDestination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Icon(
                dest.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            dest.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
