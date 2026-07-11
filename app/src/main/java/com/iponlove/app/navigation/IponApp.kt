package com.iponlove.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.iponlove.app.core.ui.CoachMarkOverlay
import com.iponlove.app.core.ui.CoachMarkState
import com.iponlove.app.core.ui.LocalCoachMarkState
import com.iponlove.app.core.ui.LocalTutorialController
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import com.iponlove.app.feature.tutorial.presentation.TutorialViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iponlove.app.feature.analysis.presentation.AnalysisScreen
import com.iponlove.app.feature.applock.presentation.AppLockSetupScreen
import com.iponlove.app.feature.calculator.presentation.CalculatorScreen
import com.iponlove.app.feature.couple.presentation.CoupleScreen
import com.iponlove.app.feature.feedback.presentation.BetaFeedbackScreen
import com.iponlove.app.feature.help.presentation.HelpScreen
import com.iponlove.app.feature.help.presentation.UpcomingFeaturesScreen
import com.iponlove.app.feature.manage.presentation.ManageScreen
import com.iponlove.app.feature.notes.presentation.NoteEditorScreen
import com.iponlove.app.feature.notes.presentation.NoteEditorViewModel.Companion.NOTE_ID_KEY
import com.iponlove.app.feature.notes.presentation.NotesScreen
import com.iponlove.app.feature.recurring.presentation.RecurringScreen
import com.iponlove.app.feature.savings.presentation.GoalDetailScreen
import com.iponlove.app.feature.savings.presentation.GoalEditorScreen
import com.iponlove.app.feature.savings.presentation.GoalEditorViewModel.Companion.GOAL_ID_KEY
import com.iponlove.app.feature.savings.presentation.SavingsGoalsScreen
import com.iponlove.app.feature.settings.presentation.PersonalizeScreen
import com.iponlove.app.feature.settings.presentation.ProfileScreen
import com.iponlove.app.feature.settings.presentation.SettingsCoupleScreen
import com.iponlove.app.feature.subscription.presentation.SubscriptionScreen
import com.iponlove.app.feature.transactions.presentation.AddTransactionScreen
import com.iponlove.app.feature.transactions.presentation.AddTransactionViewModel.Companion.TXN_ID_KEY
import com.iponlove.app.feature.transactions.presentation.TransactionsScreen
import kotlinx.coroutines.launch

private const val APP_LOCK_SETUP_ROUTE = "app_lock_setup"
private const val NOTE_EDITOR_ROUTE = "note_editor"
private const val PROFILE_ROUTE = "profile"
private const val SUBSCRIPTION_ROUTE = "subscription"
private const val SETTINGS_COUPLE_ROUTE = "settings_couple"
private const val NAV_EDITOR_ROUTE = "nav_editor"
private const val HELP_ROUTE = "help"
private const val BETA_FEEDBACK_ROUTE = "beta_feedback"
private const val UPCOMING_FEATURES_ROUTE = "upcoming_features"
private const val ADD_TRANSACTION_ROUTE = "add_transaction"
private const val EDIT_TRANSACTION_ROUTE = "edit_transaction"
private const val GOAL_EDITOR_ROUTE = "goal_editor"
private const val GOAL_DETAIL_ROUTE = "goal_detail"

/**
 * Suffix distinguishing a module's *nested graph* route from its root screen route (ADR-0033).
 * Every pinnable module is wrapped in its own nested graph so tab switches preserve each module's
 * back stack (behavior 1) and re-tapping the active tab pops it back to root (behavior 2). The
 * graph route is what the bottom bar / More sheet navigate to; the root screen route is that
 * graph's start destination.
 */
private const val GRAPH_SUFFIX = "_graph"

/** A module's own nested-graph route (see [GRAPH_SUFFIX]). */
private fun NavDestination.graphRoute(): String = route + GRAPH_SUFFIX

/**
 * App root: a bottom-nav [Scaffold] whose bar is built dynamically from the user's pinned
 * [NavConfig] (ADR-0017) — up to [NavRegistry.MAX_PINS] reorderable pins, a fixed accented center
 * ⊕ Add (ADR-0026) that routes to add-transaction, plus an always-present "More". The [NavHost]
 * declares *every* registry destination so `saveState`/`restoreState` work across the dynamic pin
 * set; pinning only changes which destinations the bar surfaces.
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
    val currentDestination = backStackEntry?.destination

    // NavHost can't swap its start without rebuilding the graph (wiping the back stack), so the
    // home destination is captured once. Reordering pins later updates the bar, not home. The
    // start is a module *graph* route (ADR-0033), which resolves to that module's root screen.
    val startGraphRoute = rememberSaveable { state.startRoute + GRAPH_SUFFIX }

    var showMore by rememberSaveable { mutableStateOf(false) }

    // Onboarding coach-marks (ADR-0038): one overlay + one CoachMarkState at the shell, shared down
    // to every feature screen via CompositionLocals so each screen arms its own first-visit tour.
    val tutorialViewModel: TutorialViewModel = hiltViewModel()
    val tutorialState by tutorialViewModel.uiState.collectAsState()
    val coachState = remember { CoachMarkState() }
    // The shell tour fires up-front at shell mount (it teaches navigation, needed before modules).
    LaunchedEffect(Unit) { tutorialViewModel.maybeStartTour(TutorialTours.SHELL) }
    // The "tap More" step advances by *observing* the sheet actually open, not by driving it.
    LaunchedEffect(showMore) {
        if (showMore) tutorialViewModel.onTargetActivated(TutorialTargets.MORE)
    }

    val visiblePins = state.visiblePinIds.mapNotNull { NavRegistry.byId[it] }
        .ifEmpty { listOf(NavRegistry.RECORDS) }

    // A tab is "selected" whenever the current destination sits anywhere inside that module's
    // nested graph — so Records stays highlighted while on its Recurring sub-screen, etc.
    fun isInGraph(dest: NavDestination): Boolean =
        currentDestination?.hierarchy?.any { it.route == dest.graphRoute() } == true

    Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(
        LocalCoachMarkState provides coachState,
        LocalTutorialController provides tutorialViewModel,
    ) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(modifier = Modifier.coachMarkTarget(TutorialTargets.PINS, coachState)) {
                // The fixed center ⊕ Add sits in the middle of the bar (ADR-0026): render the
                // first half of the pins, the accented Add slot, then the rest, then More.
                val splitIndex = ((visiblePins.size + 1) / 2).coerceAtMost(visiblePins.size)
                visiblePins.take(splitIndex).forEach { dest ->
                    PinBarItem(dest, isInGraph(dest)) { navController.switchTab(dest) }
                }
                NavigationBarItem(
                    modifier = Modifier.coachMarkTarget(TutorialTargets.ADD, coachState),
                    selected = false,
                    onClick = { navController.navigate(ADD_TRANSACTION_ROUTE) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Add transaction",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                    ),
                )
                visiblePins.drop(splitIndex).forEach { dest ->
                    PinBarItem(dest, isInGraph(dest)) { navController.switchTab(dest) }
                }
                // More is selected when we're inside a module graph that isn't a visible pin
                // (e.g. Settings and its sub-screens while Settings is unpinned).
                val inSomeModuleGraph = NavRegistry.all.any { isInGraph(it) }
                NavigationBarItem(
                    modifier = Modifier.coachMarkTarget(TutorialTargets.MORE, coachState),
                    selected = inSomeModuleGraph && visiblePins.none { isInGraph(it) },
                    onClick = { showMore = true },
                    icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = "More") },
                    label = { Text("More") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startGraphRoute,
            modifier = Modifier.padding(padding),
        ) {
            // Each pinnable module is its own nested graph (ADR-0033) so its back stack survives
            // tab switches (saveState/restoreState) and re-tapping the tab pops it back to root.

            // Records: root + Recurring (Notes moved to its own top-level module — Item 13).
            navigation(startDestination = NavRegistry.RECORDS.route, route = NavRegistry.RECORDS.graphRoute()) {
                composable(NavRegistry.RECORDS.route) {
                    TransactionsScreen(
                        onOpenRecurring = { navController.navigate(NavRegistry.RECURRING.route) },
                        onAddTransaction = { navController.navigate(ADD_TRANSACTION_ROUTE) },
                        onEditTransaction = { id -> navController.navigate("$EDIT_TRANSACTION_ROUTE/$id") },
                    )
                }
                composable(NavRegistry.RECURRING.route) {
                    RecurringScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPremium = { navController.navigate(SUBSCRIPTION_ROUTE) },
                    )
                }
            }

            // Notes: root + note editor. Own module graph (Item 13), no back arrow at root.
            navigation(startDestination = NavRegistry.NOTES.route, route = NavRegistry.NOTES.graphRoute()) {
                composable(NavRegistry.NOTES.route) {
                    NotesScreen(
                        onOpenNote = { noteId -> navController.navigate("$NOTE_EDITOR_ROUTE/$noteId") },
                    )
                }
                composable(
                    route = "$NOTE_EDITOR_ROUTE/{$NOTE_ID_KEY}",
                    arguments = listOf(navArgument(NOTE_ID_KEY) { type = NavType.StringType }),
                ) {
                    NoteEditorScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPremium = { navController.navigate(SUBSCRIPTION_ROUTE) },
                    )
                }
            }

            // Analysis: single-node graph (uniform nesting per ADR-0033 decision 1).
            navigation(startDestination = NavRegistry.ANALYSIS.route, route = NavRegistry.ANALYSIS.graphRoute()) {
                composable(NavRegistry.ANALYSIS.route) {
                    AnalysisScreen(
                        onOpenCouple = { navController.navigate(SETTINGS_COUPLE_ROUTE) },
                        onOpenPremium = { navController.navigate(SUBSCRIPTION_ROUTE) },
                    )
                }
            }

            // Manage: single-node graph.
            navigation(startDestination = NavRegistry.MANAGE.route, route = NavRegistry.MANAGE.graphRoute()) {
                composable(NavRegistry.MANAGE.route) {
                    ManageScreen(onOpenPremium = { navController.navigate(SUBSCRIPTION_ROUTE) })
                }
            }

            // Couple: single-node graph (handles unpaired inside its own screen).
            navigation(startDestination = NavRegistry.COUPLE.route, route = NavRegistry.COUPLE.graphRoute()) {
                composable(NavRegistry.COUPLE.route) {
                    CoupleScreen(onOpenPremium = { navController.navigate(SUBSCRIPTION_ROUTE) })
                }
            }

            // Calculator: single-node graph.
            navigation(startDestination = NavRegistry.CALCULATOR.route, route = NavRegistry.CALCULATOR.graphRoute()) {
                composable(NavRegistry.CALCULATOR.route) {
                    CalculatorScreen(onOpenPremium = { navController.navigate(SUBSCRIPTION_ROUTE) })
                }
            }

            // Savings: root + goal editor + goal detail.
            navigation(startDestination = NavRegistry.SAVINGS.route, route = NavRegistry.SAVINGS.graphRoute()) {
                composable(NavRegistry.SAVINGS.route) {
                    SavingsGoalsScreen(
                        onCreateGoal = { navController.navigate(GOAL_EDITOR_ROUTE) },
                        onOpenGoal = { id -> navController.navigate("$GOAL_DETAIL_ROUTE/$id") },
                    )
                }
                composable(GOAL_EDITOR_ROUTE) {
                    GoalEditorScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPremium = { navController.navigate(SUBSCRIPTION_ROUTE) },
                    )
                }
                composable(
                    route = "$GOAL_EDITOR_ROUTE/{$GOAL_ID_KEY}",
                    arguments = listOf(navArgument(GOAL_ID_KEY) { type = NavType.StringType }),
                ) {
                    GoalEditorScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPremium = { navController.navigate(SUBSCRIPTION_ROUTE) },
                    )
                }
                composable(
                    route = "$GOAL_DETAIL_ROUTE/{$GOAL_ID_KEY}",
                    arguments = listOf(navArgument(GOAL_ID_KEY) { type = NavType.StringType }),
                ) {
                    GoalDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEditGoal = { id -> navController.navigate("$GOAL_EDITOR_ROUTE/$id") },
                    )
                }
            }

            // Settings: root + all its sub-screens.
            navigation(startDestination = NavRegistry.SETTINGS.route, route = NavRegistry.SETTINGS.graphRoute()) {
                composable(NavRegistry.SETTINGS.route) {
                    PersonalizeScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { navController.navigate(PROFILE_ROUTE) },
                        onOpenPremium = { navController.navigate(SUBSCRIPTION_ROUTE) },
                        onOpenSecurity = { navController.navigate(APP_LOCK_SETUP_ROUTE) },
                        onOpenCouple = { navController.navigate(SETTINGS_COUPLE_ROUTE) },
                        onOpenNavbar = { navController.navigate(NAV_EDITOR_ROUTE) },
                        onOpenHelp = { navController.navigate(HELP_ROUTE) },
                        onOpenBetaFeedback = { navController.navigate(BETA_FEEDBACK_ROUTE) },
                        onOpenUpcomingFeatures = { navController.navigate(UPCOMING_FEATURES_ROUTE) },
                        onReplayTutorial = { tutorialViewModel.replay() },
                        onSignOut = onSignOut,
                    )
                }
                composable(PROFILE_ROUTE) {
                    ProfileScreen(onBack = { navController.popBackStack() })
                }
                composable(SUBSCRIPTION_ROUTE) {
                    SubscriptionScreen(onBack = { navController.popBackStack() })
                }
                composable(SETTINGS_COUPLE_ROUTE) {
                    SettingsCoupleScreen(onBack = { navController.popBackStack() })
                }
                composable(APP_LOCK_SETUP_ROUTE) {
                    AppLockSetupScreen(onBack = { navController.popBackStack() })
                }
                composable(HELP_ROUTE) {
                    HelpScreen(onBack = { navController.popBackStack() })
                }
                composable(BETA_FEEDBACK_ROUTE) {
                    BetaFeedbackScreen(onBack = { navController.popBackStack() })
                }
                composable(UPCOMING_FEATURES_ROUTE) {
                    UpcomingFeaturesScreen(onBack = { navController.popBackStack() })
                }
            }

            // Add/Edit Transaction stays a standalone top-level route (ADR-0033 decision 2) — it's
            // reached from the global ⊕ button, so it must not inherit any tab's reset-on-retap.
            composable(ADD_TRANSACTION_ROUTE) {
                AddTransactionScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPremium = { navController.navigate(SUBSCRIPTION_ROUTE) },
                )
            }
            // Navbar editor is also a standalone top-level route (V1.6.3 Item 6): nested inside the
            // Settings graph it stacked a second module graph over the origin tab, which made
            // switchTab's popUpTo/restoreState silently no-op for any tab with saved back-stack
            // state. As a root route, switchTab's !inSomeModuleGraph guard (ADR-0039) pops it before
            // navigating, same as Add/Edit.
            composable(NAV_EDITOR_ROUTE) {
                NavbarEditorScreen(
                    initialConfig = state.config,
                    isPaired = state.isPaired,
                    onApply = navViewModel::applyConfig,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "$EDIT_TRANSACTION_ROUTE/{$TXN_ID_KEY}",
                arguments = listOf(navArgument(TXN_ID_KEY) { type = NavType.StringType }),
            ) {
                AddTransactionScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPremium = { navController.navigate(SUBSCRIPTION_ROUTE) },
                )
            }
        }
    }
        // Coach-mark overlay draws above the Scaffold (bar included), sharing its coordinate root
        // so it can anchor to the tagged bar targets. Transparent to touches outside its tooltip.
        CoachMarkOverlay(
            state = coachState,
            step = tutorialState.currentStep,
            onPrimary = tutorialViewModel::next,
            onSkip = tutorialViewModel::skip,
        )
    }
    }

    if (showMore) {
        MoreSheet(
            modules = state.moreModuleIds.mapNotNull { NavRegistry.byId[it] },
            onModule = { dest ->
                showMore = false
                navController.switchTab(dest)
            },
            onEditNavbar = {
                showMore = false
                navController.navigate(NAV_EDITOR_ROUTE)
            },
            onDismiss = { showMore = false },
        )
    }
}

/** One pinned bottom-bar destination — extracted so the center ⊕ can split the pin list. */
@Composable
private fun RowScope.PinBarItem(
    dest: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(dest.icon, contentDescription = dest.label) },
        label = { Text(dest.label) },
    )
}

/**
 * Tab switch across module nested graphs (ADR-0033):
 *  - Behavior 1 (preserve place): navigating to another module's graph saves the current graph's
 *    back stack and restores the target's, so each module resumes where it was left.
 *  - Behavior 2 (reset to root): tapping the tab for the module you're already inside pops that
 *    module's graph back to its root screen (`popBackStack` to the graph's start destination).
 *
 * Add/Edit-transaction are top-level routes living outside every module graph (ADR-0033 dec. 2).
 * If one is on top when a tab is tapped, drop it unsaved first (ADR-0039) — otherwise the
 * subsequent `saveState = true` below would sweep it into the origin tab's saved back stack and
 * `restoreState = true` would resurrect it the next time that tab is revisited.
 */
private fun NavHostController.switchTab(dest: NavDestination) {
    val inSomeModuleGraph = NavRegistry.all.any { module ->
        currentDestination?.hierarchy?.any { it.route == module.graphRoute() } == true
    }
    if (!inSomeModuleGraph) {
        popBackStack()
    }

    val alreadyInModule = currentDestination?.hierarchy?.any { it.route == dest.graphRoute() } == true
    if (alreadyInModule) {
        popBackStack(dest.route, inclusive = false)
    } else {
        navigate(dest.graphRoute()) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
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
