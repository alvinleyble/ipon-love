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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.iponlove.app.feature.notifications.presentation.InboxBellState
import com.iponlove.app.feature.notifications.presentation.LocalInboxBell
import com.iponlove.app.feature.notifications.presentation.NotificationBellViewModel
import com.iponlove.app.feature.notifications.presentation.NotificationInboxScreen
import com.iponlove.app.feature.recurring.presentation.RecurringScreen
import com.iponlove.app.feature.savings.presentation.GoalDetailScreen
import com.iponlove.app.feature.savings.presentation.GoalEditorScreen
import com.iponlove.app.feature.savings.presentation.GoalEditorViewModel.Companion.GOAL_ID_KEY
import com.iponlove.app.feature.savings.presentation.SavingsGoalsScreen
import com.iponlove.app.feature.settings.presentation.AppearanceScreen
import com.iponlove.app.feature.settings.presentation.FinanceScreen
import com.iponlove.app.feature.settings.presentation.NotificationsScreen
import com.iponlove.app.feature.settings.presentation.AboutScreen
import com.iponlove.app.feature.settings.presentation.LicensesScreen
import com.iponlove.app.feature.settings.presentation.PersonalizeScreen
import com.iponlove.app.feature.settings.presentation.ProfileScreen
import com.iponlove.app.feature.settings.presentation.SettingsCoupleScreen
import com.iponlove.app.feature.subscription.presentation.SubscriptionScreen
import com.iponlove.app.feature.subscription.presentation.SubscriptionViewModel.Companion.DEFAULT_SOURCE
import com.iponlove.app.feature.subscription.presentation.SubscriptionViewModel.Companion.SOURCE_KEY
import com.iponlove.app.feature.transactions.presentation.AddTransactionScreen
import com.iponlove.app.feature.transactions.presentation.AddTransactionViewModel.Companion.TXN_ID_KEY
import com.iponlove.app.feature.transactions.presentation.TransactionsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val APP_LOCK_SETUP_ROUTE = "app_lock_setup"
private const val NOTE_EDITOR_ROUTE = "note_editor"
private const val PROFILE_ROUTE = "profile"
private const val APPEARANCE_ROUTE = "settings_appearance"
private const val FINANCE_ROUTE = "settings_finance"
private const val NOTIFICATIONS_ROUTE = "settings_notifications"
private const val SUBSCRIPTION_ROUTE = "subscription"
/** Paywall route carrying the entry surface as a nav arg (Item 21) — e.g. "subscription?source=budgets". */
private fun subscriptionRoute(source: String) = "$SUBSCRIPTION_ROUTE?$SOURCE_KEY=$source"
private const val SETTINGS_COUPLE_ROUTE = "settings_couple"
private const val NAV_EDITOR_ROUTE = "nav_editor"
private const val NOTIFICATION_INBOX_ROUTE = "notification_inbox"
private const val HELP_ROUTE = "help"
private const val ABOUT_ROUTE = "settings_about"
private const val LICENSES_ROUTE = "settings_licenses"
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
    deepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    isColdStart: Boolean = false,
) {
    val state by navViewModel.uiState.collectAsState()

    // Resolve the cold-start restore target BEFORE the NavHost mounts, so the restored module can
    // be its start destination and the home tab never flashes past first (Item 39). It's a single
    // DataStore read, hidden inside the cold-start splash already on screen. rememberSaveable so a
    // config change keeps the resolved value instead of re-gating with a spinner.
    var startModule by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.loaded) {
        if (state.loaded && startModule == null) {
            startModule = if (isColdStart && deepLinkRoute == null) {
                navViewModel.moduleToRestore(state.startRoute) ?: state.startRoute
            } else {
                state.startRoute
            }
        }
    }

    val resolvedStart = startModule
    if (!state.loaded || resolvedStart == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    IponAppContent(
        state = state,
        startModule = resolvedStart,
        onSignOut = onSignOut,
        navViewModel = navViewModel,
        deepLinkRoute = deepLinkRoute,
        onDeepLinkHandled = onDeepLinkHandled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IponAppContent(
    state: NavUiState,
    startModule: String,
    onSignOut: () -> Unit,
    navViewModel: NavbarViewModel,
    deepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Which top-level module (NavRegistry) the current destination sits inside — null on the
    // standalone routes that live outside every module graph (add/edit-transaction, nav editor).
    // Same graph-membership test the bottom bar uses for tab selection.
    val currentModuleId: String? = NavRegistry.all.firstOrNull { module ->
        currentDestination?.hierarchy?.any { it.route == module.graphRoute() } == true
    }?.id

    // Persist where we are as the app is backgrounded (v1.6.6 Item 39). onStop is the last reliable
    // in-process moment before the ROM may force-stop us; the disk write survives the kill so a
    // cold relaunch can restore the module below — rememberSaveable can't, since force-stop drops
    // the saved-instance-state bundle.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        navViewModel.rememberLocation(currentModuleId)
    }

    // A home-screen widget can request a module to open on launch (Item 33: balance widget →
    // Manage → Accounts). Fires once per requested route, after the NavHost graph is set; the
    // switchTab lands on the module's root (Manage defaults to its Accounts sub-tab).
    LaunchedEffect(deepLinkRoute) {
        val dest = deepLinkRoute?.let { NavRegistry.byId[it] } ?: return@LaunchedEffect
        navController.switchTab(dest)
        onDeepLinkHandled()
    }

    // NavHost can't swap its start without rebuilding the graph (wiping the back stack), so the
    // start is captured once. `startModule` is the resolved cold-start destination — the restored
    // module when returning within the window, else home (Item 39) — so a restore opens directly
    // here with no home-tab flash past first. It's a module *graph* route (ADR-0033), which
    // resolves to that module's root screen.
    val startGraphRoute = rememberSaveable { startModule + GRAPH_SUFFIX }

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
    // End a screen tour when the user navigates away from its screen: once the current step's target
    // has laid out, watch for it leaving composition (its bounds get unregistered) and dismiss the
    // tour then — this stops a stale ring bleeding onto the next screen and frees the single-active
    // slot so that screen's own tour can start. Shell-chrome targets (bottom bar) never leave, so
    // the shell tour is unaffected and correctly survives tab switches.
    val activeStepKey = tutorialState.currentStep?.targetKey
    LaunchedEffect(tutorialState.activeTourId, tutorialState.stepIndex, activeStepKey) {
        val key = activeStepKey ?: return@LaunchedEffect
        snapshotFlow { coachState.boundsOf(key) != null }.first { it }   // wait until laid out
        snapshotFlow { coachState.boundsOf(key) != null }.first { !it }  // then until it disappears
        tutorialViewModel.dismissForNavigation()
    }

    val visiblePins = state.visiblePinIds.mapNotNull { NavRegistry.byId[it] }
        .ifEmpty { listOf(NavRegistry.RECORDS) }

    // A tab is "selected" whenever the current destination sits anywhere inside that module's
    // nested graph — so Records stays highlighted while on its Recurring sub-screen, etc.
    fun isInGraph(dest: NavDestination): Boolean =
        currentDestination?.hierarchy?.any { it.route == dest.graphRoute() } == true

    // The notification bell is account-global chrome, not a per-screen action, so its state is
    // resolved once here and provided down — PlayfulScreenTitle renders it on every top-level
    // module without each screen (or its ViewModel) knowing the inbox exists (ADR-0053).
    val inboxViewModel: NotificationBellViewModel = hiltViewModel()
    val unreadCount by inboxViewModel.unreadCount.collectAsState()
    val inboxBell = InboxBellState(
        unreadCount = unreadCount,
        onOpen = { navController.navigate(NOTIFICATION_INBOX_ROUTE) },
    )

    Box(Modifier.fillMaxSize().playfulBackground()) {
    CompositionLocalProvider(
        LocalCoachMarkState provides coachState,
        LocalTutorialController provides tutorialViewModel,
        LocalInboxBell provides inboxBell,
    ) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        bottomBar = {
            // Playful Pop bottom bar (v1.6.7 Item 8) — visuals only; every handler (switchTab,
            // ADD route, More sheet) and coach-mark target is preserved from the M3 NavigationBar.
            val splitIndex = ((visiblePins.size + 1) / 2).coerceAtMost(visiblePins.size)
            val inSomeModuleGraph = NavRegistry.all.any { isInGraph(it) }
            val moreSelected = inSomeModuleGraph && visiblePins.none { isInGraph(it) }
            PlayfulBottomBar(
                modifier = Modifier.coachMarkTarget(TutorialTargets.PINS, coachState),
                addModifier = Modifier.coachMarkTarget(TutorialTargets.ADD, coachState),
                moreModifier = Modifier.coachMarkTarget(TutorialTargets.MORE, coachState),
                firstPins = visiblePins.take(splitIndex),
                lastPins = visiblePins.drop(splitIndex),
                isSelected = { isInGraph(it) },
                onPinClick = { navController.switchTab(it) },
                onAddClick = { navController.navigate(ADD_TRANSACTION_ROUTE) },
                onMoreClick = { showMore = true },
                moreSelected = moreSelected,
            )
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
                        onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) },
                    )
                }
                composable(NavRegistry.RECURRING.route) {
                    RecurringScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) },
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
                        onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) },
                    )
                }
            }

            // Analysis: single-node graph (uniform nesting per ADR-0033 decision 1).
            navigation(startDestination = NavRegistry.ANALYSIS.route, route = NavRegistry.ANALYSIS.graphRoute()) {
                composable(NavRegistry.ANALYSIS.route) {
                    AnalysisScreen(
                        onOpenCouple = { navController.navigate(SETTINGS_COUPLE_ROUTE) },
                        onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) },
                    )
                }
            }

            // Manage: single-node graph.
            navigation(startDestination = NavRegistry.MANAGE.route, route = NavRegistry.MANAGE.graphRoute()) {
                composable(NavRegistry.MANAGE.route) {
                    ManageScreen(onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) })
                }
            }

            // Couple: single-node graph (handles unpaired inside its own screen).
            navigation(startDestination = NavRegistry.COUPLE.route, route = NavRegistry.COUPLE.graphRoute()) {
                composable(NavRegistry.COUPLE.route) {
                    CoupleScreen(onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) })
                }
            }

            // Calculator: single-node graph.
            navigation(startDestination = NavRegistry.CALCULATOR.route, route = NavRegistry.CALCULATOR.graphRoute()) {
                composable(NavRegistry.CALCULATOR.route) {
                    CalculatorScreen(onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) })
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
                        onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) },
                    )
                }
                composable(
                    route = "$GOAL_EDITOR_ROUTE/{$GOAL_ID_KEY}",
                    arguments = listOf(navArgument(GOAL_ID_KEY) { type = NavType.StringType }),
                ) {
                    GoalEditorScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) },
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
                        onOpenAppearance = { navController.navigate(APPEARANCE_ROUTE) },
                        onOpenFinance = { navController.navigate(FINANCE_ROUTE) },
                        onOpenProfile = { navController.navigate(PROFILE_ROUTE) },
                        onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) },
                        onOpenSecurity = { navController.navigate(APP_LOCK_SETUP_ROUTE) },
                        onOpenNotifications = { navController.navigate(NOTIFICATIONS_ROUTE) },
                        onOpenCouple = { navController.navigate(SETTINGS_COUPLE_ROUTE) },
                        onOpenNavbar = { navController.navigate(NAV_EDITOR_ROUTE) },
                        onOpenHelp = { navController.navigate(HELP_ROUTE) },
                        onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
                        onOpenBetaFeedback = { navController.navigate(BETA_FEEDBACK_ROUTE) },
                        onOpenUpcomingFeatures = { navController.navigate(UPCOMING_FEATURES_ROUTE) },
                        onReplayTutorial = { tutorialViewModel.replay() },
                        onSignOut = onSignOut,
                    )
                }
                composable(APPEARANCE_ROUTE) {
                    AppearanceScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) },
                    )
                }
                composable(FINANCE_ROUTE) {
                    FinanceScreen(onBack = { navController.popBackStack() })
                }
                composable(NOTIFICATIONS_ROUTE) {
                    NotificationsScreen(onBack = { navController.popBackStack() })
                }
                composable(ABOUT_ROUTE) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLicenses = { navController.navigate(LICENSES_ROUTE) },
                    )
                }
                composable(LICENSES_ROUTE) {
                    LicensesScreen(onBack = { navController.popBackStack() })
                }
                composable(PROFILE_ROUTE) {
                    ProfileScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = "$SUBSCRIPTION_ROUTE?$SOURCE_KEY={$SOURCE_KEY}",
                    arguments = listOf(navArgument(SOURCE_KEY) {
                        type = NavType.StringType
                        defaultValue = DEFAULT_SOURCE
                    }),
                ) {
                    SubscriptionScreen(onBack = { navController.popBackStack() })
                }
                composable(SETTINGS_COUPLE_ROUTE) {
                    SettingsCoupleScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) },
                    )
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
                    onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) },
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
                    onOpenPremium = { source -> navController.navigate(subscriptionRoute(source)) },
                )
            }
            // Notification inbox (ADR-0053): a standalone top-level route, like Add/Edit and the
            // navbar editor — it is reachable from EVERY module's bell, so nesting it inside any
            // one module's graph would stack a second module graph over the origin tab.
            composable(NOTIFICATION_INBOX_ROUTE) {
                NotificationInboxScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDeepLink = { route ->
                        NavRegistry.byId[route]?.let { dest ->
                            navController.popBackStack()
                            navController.switchTab(dest)
                        }
                    },
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

/**
 * The Playful Pop bottom bar (v1.6.7 Item 8): an opaque plum surface with a hairline top edge, a
 * pink-pill active slot, and a −4° rotated squircle FAB that rides raised at the bar's top edge.
 * Pure re-skin — the pin/add/more click handlers and coach-mark targets are passed in unchanged.
 */
@Composable
private fun PlayfulBottomBar(
    firstPins: List<NavDestination>,
    lastPins: List<NavDestination>,
    isSelected: (NavDestination) -> Boolean,
    onPinClick: (NavDestination) -> Unit,
    onAddClick: () -> Unit,
    onMoreClick: () -> Unit,
    moreSelected: Boolean,
    modifier: Modifier = Modifier,
    addModifier: Modifier = Modifier,
    moreModifier: Modifier = Modifier,
) {
    val colors = LocalPlayfulColors.current
    // Navigation-bar inset: the M3 NavigationBar this reskin replaced consumed it automatically,
    // so without it the plum bar draws under the system nav (3-button devices) — restored here.
    // The plum surface extends down behind the system bar (edge-to-edge) while its content padding
    // lifts the icons/labels above it. Zero on gesture-nav devices, so visuals are unchanged there.
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier = modifier.fillMaxWidth().height(74.dp + navBarInset)) {
        // Opaque nav surface anchored to the bottom, leaving a transparent strip up top for the
        // raised FAB to poke into (kept within the bar's own bounds so nothing gets clipped).
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(62.dp + navBarInset)
                .background(colors.navSurface)
                .drawBehind {
                    drawRect(
                        color = colors.hairline,
                        size = Size(size.width, 1.dp.toPx()),
                    )
                }
                .padding(bottom = 6.dp + navBarInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            firstPins.forEach { dest ->
                PlayfulNavSlot(dest.icon, dest.label, isSelected(dest), Modifier.weight(1f)) { onPinClick(dest) }
            }
            Spacer(Modifier.weight(1f)) // reserved center slot for the FAB overlay
            lastPins.forEach { dest ->
                PlayfulNavSlot(dest.icon, dest.label, isSelected(dest), Modifier.weight(1f)) { onPinClick(dest) }
            }
            PlayfulNavSlot(Icons.Filled.MoreHoriz, "More", moreSelected, moreModifier.weight(1f), onClick = onMoreClick)
        }
        // The raised FAB, horizontally centered over its reserved slot.
        PlayfulFab(
            modifier = addModifier.align(Alignment.TopCenter).padding(top = 4.dp),
            onClick = onAddClick,
        )
    }
}

/** One bottom-bar slot: a pink-pill icon holder (when [selected]) above a label. */
@Composable
private fun PlayfulNavSlot(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (selected) Modifier
                        .rotate(-4f)
                        .size(width = 52.dp, height = 30.dp)
                        .clip(LeafShapes.leaf(14.dp, 5.dp))
                        .background(colors.accent)
                    else Modifier.height(30.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) colors.onAccent else colors.navInactive,
                modifier = Modifier
                    .size(22.dp)
                    .then(if (selected) Modifier.rotate(4f) else Modifier),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.textPrimary else colors.navInactive,
            maxLines = 1,
        )
    }
}

/** The center −4° squircle FAB → add-transaction. */
@Composable
private fun PlayfulFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = modifier
            .rotate(-4f)
            .size(56.dp)
            .clip(LeafShapes.leaf(20.dp, 8.dp))
            .background(colors.accent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add transaction",
            tint = colors.onAccent,
            modifier = Modifier.size(27.dp).rotate(4f),
        )
    }
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
