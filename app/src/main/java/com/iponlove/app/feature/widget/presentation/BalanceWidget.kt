package com.iponlove.app.feature.widget.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.WorkManager
import com.iponlove.app.MainActivity
import com.iponlove.app.R
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.LastActiveUserStore
import com.iponlove.app.core.session.userIdOrNull
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountBalancesUseCase
import com.iponlove.app.feature.accounts.domain.usecase.ObserveNetAssetsUseCase
import com.iponlove.app.feature.widget.data.WidgetSessionStore
import com.iponlove.app.feature.widget.data.resolveWidgetSession
import com.iponlove.app.feature.settings.domain.usecase.ObserveCurrencySymbolUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val MASKED = "•••••"

/**
 * Home-screen widget showing **net assets** (own + shared-by-me active accounts — ADR-0011/0007).
 * `SizeMode.Responsive` (Item 33): the [COMPACT] size renders the net-assets glance alone; resized
 * to [TALL] it adds a scrollable per-account breakdown ([ObserveAccountBalancesUseCase]) below the
 * same header, so the rows sum to the headline figure. Glance can't `@Inject` and paints a one-shot
 * snapshot per [provideGlance] (it can't observe a Flow), so it reaches the use cases via
 * [WidgetEntryPoint] and reads snapshots with `.first()`; accuracy then depends on every
 * balance-changing write calling [Widgets.updateAll]. The privacy mask and the in-widget eye
 * toggle live in [resolveWidgetDisplay]; the list mirrors it via [buildAccountRows] (suppressed
 * when signed out). The app lock does NOT gate the widget — like quick-add, no unlock needed
 * (Alvin's on-device call, 2026-07-14). Body + row taps open Manage → Accounts (grill 2026-07-14).
 */
class BalanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

        // Session state for the mask/eye comes from a fast local hint (Item 36) written on every auth
        // transition by WidgetSessionHintWriter — reading it never blocks on the Supabase SDK's
        // cold-start session read, which on a frozen process took up to 8s and, on timeout,
        // hard-masked logged-in users (hiding the eye) and stalled taps. The live probe runs only in
        // the one-time migration gap where the hint was never written; its result is seeded so the
        // gap self-closes.
        val store = ep.widgetSessionStore()
        val resolution = resolveWidgetSession(store.hasSession()) {
            withTimeoutOrNull(FALLBACK_PROBE_MS) {
                ep.authRepository().status.first { it !is AuthStatus.Loading }
            }?.let { it is AuthStatus.Authenticated }
        }
        resolution.seedHint?.let { store.set(it) }
        val hasSession = resolution.hasSession

        // Resolve the read-scope user id: the live Supabase session if it's up, else the persisted
        // last-active id (Item 10 follow-up). We deliberately do NOT block on the live session here.
        // The old `withTimeoutOrNull(SESSION_LOAD_MS)` wait for Authenticated stalled every
        // cold/backgrounded render up to 8s (the session never wakes in that context), and the widget
        // host drops/coalesces a render that slow — so a manual ⟳ often no-op'd and needed several
        // taps to apply (Alvin, Item 10). The account reads are local Room; with the persisted id we
        // can paint them immediately, and remote freshness comes from the background sync's
        // Widgets.updateAll, not from stalling the render. The signed-out state stays gated to
        // HardMasked upstream by the fast session hint, independent of this id.
        val resolvedUserId = ep.currentUserProvider().userIdOrNull() ?: ep.lastActiveUserStore().lastUserId()
        val sessionReady = hasSession && resolvedUserId != null

        // When we're about to render NotReady, actively poll for the session to resolve — Fix A's
        // transition-based repaint can't fire for a warm process that re-binds during a transient
        // blip, so without this the "Updating…" placeholder could hang until an app-open/write/⟳.
        // When we're ready (or signed out), cancel any pending chain so a resolved widget stops polling.
        if (hasSession && !sessionReady) {
            BalanceWidgetRetryWorker.schedule(context)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(BalanceWidgetRetryWorker.WORK_NAME)
        }

        val netAssets = ep.netAssetsUseCase().invoke().first()
        val accountBalances = ep.accountBalancesUseCase().invoke().first()
        val symbol = ep.currencySymbolUseCase().invoke().first()

        provideContent {
            val prefs = currentState<Preferences>()
            val userToggled = prefs[USER_TOGGLED_KEY] ?: false
            val display = resolveWidgetDisplay(
                netAssets = netAssets,
                symbol = symbol,
                hasSession = hasSession,
                sessionReady = sessionReady,
                userToggled = userToggled,
            )
            // Responsive picks the closest declared size; anything at least the tall height shows
            // the list form (the compact form stays byte-identical to the Item 32 glance).
            val tall = LocalSize.current.height >= TALL.height
            // Brand Rose light/dark, not the default wallpaper-derived dynamic colors (Alvin,
            // 2026-07-14 — the widget came out purple beside the pink quick-add widget).
            GlanceTheme(colors = IponWidgetColors) {
                if (tall) {
                    TallWidget(display, buildAccountRows(display, accountBalances, symbol))
                } else {
                    CompactWidget(display)
                }
            }
        }
    }

    companion object {
        val USER_TOGGLED_KEY = booleanPreferencesKey("balance_widget_user_toggled")
        // Only used in the rare migration-gap probe (hint never written); the common path reads the
        // persisted hint with no wait. Never hang forever regardless.
        private const val FALLBACK_PROBE_MS = 4000L
        private val COMPACT = DpSize(110.dp, 64.dp)
        private val TALL = DpSize(180.dp, 220.dp)
    }
}

/** Hilt bridge for a Glance widget (which is not itself an injectable component). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun netAssetsUseCase(): ObserveNetAssetsUseCase
    fun accountBalancesUseCase(): ObserveAccountBalancesUseCase
    fun currencySymbolUseCase(): ObserveCurrencySymbolUseCase
    fun authRepository(): AuthRepository
    fun widgetSessionStore(): WidgetSessionStore
    fun currentUserProvider(): CurrentUserProvider
    fun lastActiveUserStore(): LastActiveUserStore
}

/** Intent that brings the (singleTask) app forward and lands on Manage → Accounts (its default tab). */
private fun manageAccountsIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_START_ROUTE, MainActivity.ROUTE_MANAGE)

/** Compact form (≈2×1): the Item 32 net-assets glance, unchanged. */
@Composable
private fun CompactWidget(display: WidgetDisplay) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(PlayfulWidgetColors.card)
            .clickable(actionStartActivity(manageAccountsIntent(context)))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderLabelRow()
        Spacer(GlanceModifier.height(4.dp))
        AmountRow(display)
    }
}

/** Tall form: the same header over a scrollable per-account list (or a lock/empty hint). */
@Composable
private fun TallWidget(display: WidgetDisplay, rows: List<AccountRowDisplay>?) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(PlayfulWidgetColors.card)
            .padding(14.dp),
    ) {
        // Header + amount tap opens Manage → Accounts. A LazyColumn's items each need their own
        // click (an outer clickable doesn't cover scrolled rows), so the list area is handled per row.
        Column(modifier = GlanceModifier.clickable(actionStartActivity(manageAccountsIntent(context)))) {
            HeaderLabelRow()
            Spacer(GlanceModifier.height(4.dp))
            AmountRow(display)
        }
        Spacer(GlanceModifier.height(10.dp))
        when {
            display is WidgetDisplay.NotReady -> Hint("Updating…")  // session still restoring
            rows == null -> Hint("Sign in to view")                // signed out: list suppressed
            rows.isEmpty() -> Hint("No accounts yet")
            else -> LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(rows) { row -> AccountRow(row) }
            }
        }
    }
}

/** Heart + "Net assets" label + manual-refresh icon — identical in both forms. */
@Composable
private fun HeaderLabelRow() {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_heart),
            contentDescription = null,
            colorFilter = ColorFilter.tint(PlayfulWidgetColors.accent),
            modifier = GlanceModifier.size(14.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = "Net assets",
            style = TextStyle(
                color = PlayfulWidgetColors.onCardSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        // The bare 18dp icon was near-impossible to hit (Alvin, Item 10) — a mis-tap fell through
        // to the card's open-app click. Wrap it in a generous tap box, same pattern as the eye.
        Box(
            modifier = GlanceModifier
                .width(48.dp)
                .height(40.dp)
                .clickable(actionRunCallback<RefreshBalanceWidgetAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = "Refresh",
                colorFilter = ColorFilter.tint(PlayfulWidgetColors.onCardSecondary),
                modifier = GlanceModifier.size(20.dp),
            )
        }
    }
}

/** The net-assets figure + the soft-reveal eye (eye shown only in the [WidgetDisplay.Soft] state). */
@Composable
private fun AmountRow(display: WidgetDisplay) {
    val amountText = when (display) {
        WidgetDisplay.HardMasked -> MASKED
        WidgetDisplay.NotReady -> MASKED
        is WidgetDisplay.Soft -> display.text ?: MASKED
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = amountText,
            style = TextStyle(
                color = PlayfulWidgetColors.onCardPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        if (display is WidgetDisplay.Soft) {
            // Pinned to the row's far right (not floating after the amount, whose width changes
            // between masked and revealed) and wrapped in a generous tap box — the bare 22dp icon
            // was mis-tapped into the open-app body click ~80% of the time (Alvin, 2026-07-14).
            // Box enlarged 48×36 → 48×40 and the glyph 24 → 26 (Item 10: still read too small).
            Box(
                modifier = GlanceModifier
                    .width(48.dp)
                    .height(40.dp)
                    .clickable(actionRunCallback<ToggleBalanceRevealAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(
                        if (display.revealed) R.drawable.ic_widget_eye_off
                        else R.drawable.ic_widget_eye,
                    ),
                    contentDescription = if (display.revealed) "Hide amount" else "Show amount",
                    colorFilter = ColorFilter.tint(PlayfulWidgetColors.onCardSecondary),
                    modifier = GlanceModifier.size(26.dp),
                )
            }
        }
    }
}

/** One account row: color dot · name · masked-or-real balance; taps open Manage → Accounts. */
@Composable
private fun AccountRow(row: AccountRowDisplay) {
    val context = LocalContext.current
    val dotColor: ColorProvider =
        parseHexColor(row.colorHex)?.let { ColorProvider(it) } ?: PlayfulWidgetColors.accent
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(manageAccountsIntent(context)))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(dotColor)) {}
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = row.name,
            maxLines = 1,
            style = TextStyle(color = PlayfulWidgetColors.onCardPrimary, fontSize = 13.sp),
        )
        Spacer(GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = row.amountText ?: MASKED,
            maxLines = 1,
            style = TextStyle(
                color = PlayfulWidgetColors.onCardSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** Muted single-line placeholder for the list area (lock hint / empty state). */
@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = TextStyle(color = PlayfulWidgetColors.onCardSecondary, fontSize = 13.sp),
    )
}
