package com.iponlove.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.session.AccountSwitchGuard
import com.iponlove.app.core.sync.CoupleChannelManager
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.core.sync.SyncWorker
import com.iponlove.app.core.ui.LocalCurrencySymbol
import com.iponlove.app.core.ui.LocalPrivacyMode
import com.iponlove.app.core.ui.LocalTogglePrivacyMode
import com.iponlove.app.feature.budgets.worker.BudgetAlertWorker
import com.iponlove.app.feature.partnerdebt.worker.PartnerDebtNotificationWorker
import com.iponlove.app.feature.recurring.worker.RecurringReminderWorker
import com.iponlove.app.core.ui.theme.IponTheme
import com.iponlove.app.feature.applock.domain.model.AppLockPreferences
import com.iponlove.app.feature.applock.domain.usecase.ObserveAppLockUseCase
import com.iponlove.app.feature.applock.presentation.AppLockManager
import com.iponlove.app.feature.applock.presentation.LockScreen
import com.iponlove.app.feature.appupdate.presentation.AppVersionGateManager
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.presentation.AuthScreen
import com.iponlove.app.feature.auth.presentation.AuthViewModel
import com.iponlove.app.feature.auth.presentation.ForgotPasswordScreen
import com.iponlove.app.feature.auth.presentation.ForgotPasswordViewModel
import com.iponlove.app.feature.auth.presentation.ResetPasswordScreen
import com.iponlove.app.feature.auth.presentation.ResetPasswordViewModel
import com.iponlove.app.feature.couple.domain.usecase.WatchUnpairUseCase
import com.iponlove.app.feature.onboarding.domain.usecase.ShouldShowOnboardingUseCase
import com.iponlove.app.feature.recurring.domain.usecase.MaterializeRecurringRulesUseCase
import com.iponlove.app.feature.settings.data.ThemeDraftRepository
import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.settings.domain.usecase.ObserveCurrencySymbolUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveRecurringSweepArmedUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObservePrivacyModeUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveThemePreferencesUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetPrivacyModeUseCase
import com.iponlove.app.feature.user.domain.usecase.EnsureCurrentUserRowUseCase
import com.iponlove.app.feature.widget.presentation.Widgets
import com.iponlove.app.navigation.IponApp
import com.iponlove.app.navigation.OnboardingGraph
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    // A one-shot module route requested by a home-screen widget tap (e.g. the balance widget →
    // Manage → Accounts, Item 33). Consumed by IponApp once the shell is mounted, then cleared;
    // set from both onCreate (cold launch) and onNewIntent (singleTask — app already running).
    private val pendingWidgetRoute = MutableStateFlow<String?>(null)

    @Inject lateinit var supabaseClient: SupabaseClient
    @Inject lateinit var materializeRecurringRules: MaterializeRecurringRulesUseCase
    @Inject lateinit var ensureCurrentUserRow: EnsureCurrentUserRowUseCase
    @Inject lateinit var watchUnpair: WatchUnpairUseCase
    @Inject lateinit var shouldShowOnboarding: ShouldShowOnboardingUseCase
    @Inject lateinit var observeThemePreferences: ObserveThemePreferencesUseCase
    @Inject lateinit var observePrivacyMode: ObservePrivacyModeUseCase
    @Inject lateinit var setPrivacyMode: SetPrivacyModeUseCase
    @Inject lateinit var observeCurrencySymbol: ObserveCurrencySymbolUseCase
    @Inject lateinit var observeAppLock: ObserveAppLockUseCase
    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var appVersionGateManager: AppVersionGateManager
    @Inject lateinit var syncEngine: SyncEngine
    @Inject lateinit var coupleChannelManager: CoupleChannelManager
    @Inject lateinit var themeDraft: ThemeDraftRepository
    @Inject lateinit var accountSwitchGuard: AccountSwitchGuard
    @Inject lateinit var premiumGate: PremiumGate
    @Inject lateinit var observeRecurringSweepArmed: ObserveRecurringSweepArmedUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Null on a fresh launch or a force-stop respawn (Android drops the saved-instance-state
        // bundle on force-stop) → the nav shell restores the user's last module (Item 39). Non-null
        // on a config-change / low-memory recreation, where the NavHost restores its own back stack.
        val coldStart = savedInstanceState == null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleAuthDeepLink(intent)
        consumeWidgetRoute(intent)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appLockManager.cancelAutoLock()
                coupleChannelManager.setForeground(true)
                if (BuildConfig.IS_BETA_BUILD) {
                    // Own coroutine, separate from the sync/materialize flow below — a slow
                    // or offline version check must never delay those (fail-open, ADR-0029).
                    lifecycleScope.launch {
                        appVersionGateManager.check(BuildConfig.VERSION_CODE)
                    }
                }
                lifecycleScope.launch {
                    // Full refresh on every foreground resume — not only on login — so a
                    // partner's changes (and our own pending rows) converge immediately on
                    // return. Both calls are guarded by an active session so they're a no-op on
                    // the auth screen (materialize reads categories via CurrentUserProvider, which
                    // throws when logged out); sync shares the engine's single-flight lock, so it
                    // coalesces with the bell's catch-up pull rather than double-syncing.
                    if (supabaseClient.auth.currentUserOrNull() != null) {
                        materializeRecurringRules()
                        runCatching { syncEngine.sync() }
                        // Repaint the widgets with whatever the foreground sync pulled. Unlike the
                        // background SyncWorker, the in-process foreground sync didn't otherwise
                        // refresh them, so a just-added widget only caught up on the next onStop or
                        // the 30-min tick — slow on a fresh setup (Item 36).
                        Widgets.updateAll(applicationContext)
                    }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                appLockManager.scheduleAutoLock()
                coupleChannelManager.setForeground(false)
                // Repaint the home-screen widgets on background: leaving the app is exactly when
                // the user is about to look at them, and it's the last reliable in-process moment
                // before the ROM may freeze us (Alvin's device defers Glance dispatch minutes
                // otherwise — on-device find, 2026-07-14). Also picks up any in-app privacy toggle.
                lifecycleScope.launch { Widgets.updateAll(applicationContext) }
            }
        })
        enableEdgeToEdge()
        setContent {
            val savedTheme by observeThemePreferences()
                .collectAsState(initial = ThemePreferences())
            val draftTheme by themeDraft.draft.collectAsState()
            // G8: derive the *effective* palette app-wide from the live lock state. A locked
            // Premium palette renders as the free default everywhere (not just on Personalize),
            // and re-grant/enforcement-off auto-restores the chosen one — non-destructively, since
            // the chosen palette is never written down, only downgraded at read.
            val paletteLocked by premiumGate.observeLocked()
                .collectAsState(initial = false)
            val merged = draftTheme ?: savedTheme
            val themePreferences = merged.copy(
                palette = merged.palette.effective(paletteLocked),
            )
            val appLockPrefs by observeAppLock()
                .collectAsState(initial = AppLockPreferences())
            val isLocked by appLockManager.isLocked.collectAsState()
            // Seeds hidden (Item 25): privacy mode is session-scoped and defaults ON, so the first
            // frame masks amounts until the session flag emits (which is also true on cold open).
            val privacyModeOn by observePrivacyMode().collectAsState(initial = true)
            val currencySymbol by observeCurrencySymbol().collectAsState(initial = CurrencySymbol.DEFAULT)

            CompositionLocalProvider(
                LocalPrivacyMode provides privacyModeOn,
                LocalTogglePrivacyMode provides { lifecycleScope.launch { setPrivacyMode(!privacyModeOn) } },
                LocalCurrencySymbol provides currencySymbol,
            ) {
            IponTheme(themePreferences = themePreferences) {
                val authViewModel: AuthViewModel = hiltViewModel()
                val status by authViewModel.status.collectAsState()
                // Drive the live-sync couple channel: connect when an authenticated, paired
                // user is foregrounded; tear down on sign-out (ADR-0015).
                LaunchedEffect(status) {
                    coupleChannelManager.setAuthenticatedUser(
                        (status as? AuthStatus.Authenticated)?.userId,
                    )
                }
                when (val current = status) {
                    is AuthStatus.Authenticated -> {
                        // One atomic decision, not two states: null = undecided (splash), true =
                        // onboard, false = main app. Splitting this into a separate `syncDone` flag
                        // plus a lagging `showOnboarding` opened a one-frame window where the gate
                        // read "done but not onboarding" and mounted IponApp for a brand-new,
                        // not-yet-paired user — constructing the Activity-scoped NavbarViewModel too
                        // early, which then froze its pairing state stale across onboarding (F11).
                        var onboardingDecision by remember(current.userId) {
                            mutableStateOf<Boolean?>(null)
                        }
                        LaunchedEffect(current.userId) {
                            // Defensive net for a sign-out that never wiped (crash, reinstall,
                            // restored session): purge stale local data before the first sync
                            // if a different account is now signed in (ADR-0021).
                            accountSwitchGuard.onAuthenticated(current.userId)
                            ensureCurrentUserRow()
                            // Schedule the durable background sync *before* the awaited foreground
                            // sync below (Item 10 follow-up). WorkManager survives a stalled sync
                            // and even a ROM force-stop the instant we background, and its SyncWorker
                            // repaints the home-screen widget when the pull lands. Gating this behind
                            // sync() (as it was) could strand the first pull: an account switch had
                            // already wiped Room, so a login-then-immediate-background left the widget
                            // on "No accounts yet" with nothing scheduled to heal it until the app was
                            // reopened. Enqueued early, the background pull runs on its own and heals it.
                            val wm = WorkManager.getInstance(applicationContext)
                            wm.enqueueUniqueWork(
                                SyncWorker.WORK_NAME,
                                ExistingWorkPolicy.KEEP,
                                SyncWorker.buildRequest(),
                            )
                            // The new-user gate (ADR-0024) reads *this call's own* outcome —
                            // never local emptiness (would duplicate-seed a reinstall/second
                            // device) and never the shared syncEngine.state snapshot (F4: a
                            // concurrent onStart() sync can coalesce this call and leave state
                            // pointing at a stale Syncing/prior-user Success read at the wrong
                            // instant). sync() now awaits the actual result of whichever run
                            // this call raced with, so the Boolean it returns is always the
                            // right answer for this login. A failed/offline sync defers to the
                            // next launch.
                            val syncSucceeded = runCatching { syncEngine.sync() }.getOrDefault(false)
                            // Single write — the gate never observes "decided" without the answer.
                            onboardingDecision = shouldShowOnboarding(syncSucceeded)
                            materializeRecurringRules()
                            wm.enqueueUniqueWork(
                                BudgetAlertWorker.WORK_NAME,
                                ExistingWorkPolicy.REPLACE,
                                BudgetAlertWorker.buildRequest(),
                            )
                            wm.enqueueUniqueWork(
                                RecurringReminderWorker.WORK_NAME,
                                ExistingWorkPolicy.REPLACE,
                                RecurringReminderWorker.buildRequest(),
                            )
                            wm.enqueueUniqueWork(
                                PartnerDebtNotificationWorker.WORK_NAME,
                                ExistingWorkPolicy.REPLACE,
                                PartnerDebtNotificationWorker.buildRequest(),
                            )
                            Widgets.updateAll(applicationContext)
                        }
                        LaunchedEffect(current.userId) { watchUnpair() }
                        // Bring the opt-in off-app sweep in line with the stored preference
                        // (ADR-0056 decision 8). Idempotent both ways: KEEP never resets an
                        // already-running schedule's timer, and passing `false` tears down a
                        // schedule orphaned by a cancel that didn't land. The preference is
                        // device-global, so a different account signing in inherits it.
                        //
                        // Deliberately its OWN effect rather than the tail of the login effect
                        // above: that one awaits syncEngine.sync() and then the unguarded
                        // materializeRecurringRules(), so anything appended after them is silently
                        // skipped whenever either throws or the effect is cancelled. Sitting there
                        // is what stopped the sweep re-arming after a sign-out/sign-in round trip
                        // (found on-device). Reconciling the schedule reads two DataStore flags and
                        // needs neither sync nor materialization, so nothing justifies the coupling.
                        LaunchedEffect(current.userId) {
                            RecurringReminderWorker.setPeriodicEnabled(
                                applicationContext,
                                observeRecurringSweepArmed().first(),
                            )
                        }
                        // Keep IponApp always composed — never a branch swap. Swapping the
                        // NavHost out on lock tears down the NavController and every nav-scoped
                        // ViewModel, destroying in-progress drafts app-wide on unlock (ADR-0023).
                        when (onboardingDecision) {
                            null -> SplashScreen()
                            true -> OnboardingGraph(onComplete = { onboardingDecision = false })
                            false -> {
                                // Repaint the widgets once the main app mounts. On a fresh setup the
                                // post-login repaint (above) ran against an empty Room *before*
                                // onboarding created any accounts; this fires after they exist, so a
                                // widget added during setup populates without waiting (Item 36).
                                LaunchedEffect(Unit) { Widgets.updateAll(applicationContext) }
                                val form by authViewModel.form.collectAsState()
                                val widgetRoute by pendingWidgetRoute.collectAsState()
                                IponApp(
                                    onSignOut = authViewModel::signOut,
                                    deepLinkRoute = widgetRoute,
                                    onDeepLinkHandled = { pendingWidgetRoute.value = null },
                                    isColdStart = coldStart,
                                )
                                if (form.signOutPendingConfirm) {
                                    SignOutPendingDialog(
                                        onConfirm = authViewModel::confirmSignOutDiscardingChanges,
                                        onDismiss = authViewModel::cancelSignOut,
                                    )
                                }
                            }
                        }
                        // The lock must render as its own platform Window (a Dialog), not a Box
                        // sibling inside the same window as IponApp: any AlertDialog/Dialog open
                        // underneath (e.g. an in-progress transaction editor) is its own top-level
                        // Window that always draws above the Activity's main window content, so a
                        // same-window Box overlay would leave that draft's sensitive data visible
                        // on top of the lock screen. A Dialog window, added after those, is
                        // guaranteed to stack above every window opened before it.
                        if (appLockPrefs.isPinSet && isLocked) {
                            Dialog(
                                onDismissRequest = {},
                                properties = DialogProperties(
                                    usePlatformDefaultWidth = false,
                                    dismissOnBackPress = false,
                                    dismissOnClickOutside = false,
                                ),
                            ) {
                                LockScreen(isBiometricEnabled = appLockPrefs.isBiometricEnabled)
                            }
                        }
                    }

                    is AuthStatus.PasswordRecovery -> {
                        // Never falls through to the Authenticated branch above — a recovery
                        // session exists only to let the user set a new password (ADR-0027).
                        val resetPasswordViewModel: ResetPasswordViewModel = hiltViewModel()
                        ResetPasswordScreen(viewModel = resetPasswordViewModel)
                    }

                    AuthStatus.Unauthenticated -> {
                        // Sign-out (or a never-signed-in launch): repaint the balance widget so it
                        // masks the previous session's figure off the home screen (grill 2026-07-14).
                        LaunchedEffect(Unit) { Widgets.updateAll(applicationContext) }
                        var showForgotPassword by remember { mutableStateOf(false) }
                        if (showForgotPassword) {
                            val forgotPasswordViewModel: ForgotPasswordViewModel = hiltViewModel()
                            ForgotPasswordScreen(
                                viewModel = forgotPasswordViewModel,
                                onBack = { showForgotPassword = false },
                            )
                        } else {
                            AuthScreen(
                                viewModel = authViewModel,
                                onForgotPassword = { showForgotPassword = true },
                            )
                        }
                    }

                    AuthStatus.Loading -> SplashScreen()
                }

                // Declared last so this Window stacks above every other Dialog opened above
                // (including the AppLock lock screen) — a stale beta build blocks everything,
                // not just the authenticated app shell. Beta-only; runs pre-login too, since
                // the onStart observer that populates it is registered unconditionally.
                if (BuildConfig.IS_BETA_BUILD) {
                    val isVersionMismatched by appVersionGateManager.isBlocked.collectAsState()
                    if (isVersionMismatched) {
                        Dialog(
                            onDismissRequest = {},
                            properties = DialogProperties(
                                usePlatformDefaultWidth = false,
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false,
                            ),
                        ) {
                            UpdateRequiredScreen(
                                onUpdateClick = {
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL)),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: keep the Activity's intent current so any later reads see this one, then
        // handle both the auth deep link and a widget's requested route.
        setIntent(intent)
        handleAuthDeepLink(intent)
        consumeWidgetRoute(intent)
    }

    /** Latch a widget-requested module route (once), stripping the extra so it can't re-fire on a
     *  later Activity recreation (e.g. rotation) after it's already been navigated. */
    private fun consumeWidgetRoute(intent: Intent) {
        val route = intent.getStringExtra(EXTRA_START_ROUTE) ?: return
        intent.removeExtra(EXTRA_START_ROUTE)
        pendingWidgetRoute.value = route
    }

    private fun handleAuthDeepLink(intent: Intent) {
        // handleDeeplinks defaults both callbacks to silent no-ops — without them, a parse
        // failure (e.g. wrong flow type, malformed redirect) leaves no trace anywhere. Logged
        // temporarily while diagnosing the password-recovery deep link (ADR-0027).
        Log.d(TAG, "handleAuthDeepLink: action=${intent.action} data=${intent.data}")
        supabaseClient.handleDeeplinks(
            intent,
            { session -> Log.d(TAG, "handleDeeplinks: session established, type=${session.type}") },
            { error -> Log.w(TAG, "handleDeeplinks: failed to parse deep link", error) },
        )
    }

    companion object {
        /** Intent extra: a [NavRegistry] module id to open on launch, set by a widget tap (Item 33). */
        const val EXTRA_START_ROUTE = "com.iponlove.app.extra.START_ROUTE"

        /** [EXTRA_START_ROUTE] value for Manage (whose default sub-tab is Accounts). */
        const val ROUTE_MANAGE = "manage"

        /** [EXTRA_START_ROUTE] value for Records — hosts the "To confirm" card recurring
         *  due-date reminders deep-link to (v1.7.1 Item 1, ADR-0052 decision 6). */
        const val ROUTE_RECORDS = "records"

        /** [EXTRA_START_ROUTE] value for Couple — the partner-debt alert's tap target (v1.7.1
         *  Item 9). Lands on the module root (Combined tab), one tap from Debts; jumping straight
         *  to the specific debt row would need extending the deep-link plumbing past module-root
         *  (flagged, not built here — a scope call, not an oversight). */
        const val ROUTE_COUPLE = "couple"

        private const val TAG = "MainActivity"
    }
}

@Composable
private fun SplashScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

// The web URL degrades gracefully via a browser if the Play Store app isn't installed;
// every tester who could see this dialog is already enrolled in internal testing, since
// that's the only way to have the app installed at all (ADR-0029).
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.iponlove.app"

/** Hard, non-dismissable block shown when the installed build doesn't exact-match the
 * Supabase-published required version (ADR-0029, beta only). */
@Composable
private fun UpdateRequiredScreen(onUpdateClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Update required", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "This beta build is out of date. Update to the latest version from the Play " +
                    "Store to keep testing.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUpdateClick) { Text("Update") }
        }
    }
}

/**
 * Shown when sign-out couldn't sync pending changes (offline). Signing out wipes local data,
 * so confirm before discarding the unsynced changes (ADR-0021).
 */
@Composable
private fun SignOutPendingDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unsynced changes") },
        text = {
            Text(
                "We couldn't sync your latest changes — you may be offline. Signing out now " +
                    "will erase them from this device. Sign out anyway?",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sign out") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Stay signed in") }
        },
    )
}
