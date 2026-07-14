package com.iponlove.app.feature.widget.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
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
import com.iponlove.app.MainActivity
import com.iponlove.app.R
import com.iponlove.app.feature.applock.presentation.AppLockManager
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.applock.domain.usecase.ObserveAppLockUseCase
import com.iponlove.app.feature.accounts.domain.usecase.ObserveNetAssetsUseCase
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.settings.domain.usecase.ObserveCurrencySymbolUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObservePrivacyModeUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val MASKED = "•••••"

/**
 * Home-screen widget showing **net assets** (own + shared-by-me active accounts — ADR-0011/0007).
 * Glance can't `@Inject` and paints a one-shot snapshot per [provideGlance] (it can't observe a
 * Flow), so it reaches the use cases via [WidgetEntryPoint] and reads snapshots with `.first()`;
 * accuracy then depends on every balance-changing write calling [Widgets.updateAll]. The privacy /
 * lock mask and the in-widget eye toggle live in [resolveWidgetDisplay] (grill 2026-07-14).
 */
class BalanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

        val netAssets = ep.netAssetsUseCase().invoke().first()
        val symbol = ep.currencySymbolUseCase().invoke().first()
        val globalHide = ep.privacyModeUseCase().invoke().first()
        val appLock = ep.appLockUseCase().invoke().first()
        val isLocked = ep.appLockManager().isLocked.value
        // Wait past the brief Loading window so a real session isn't misread as "logged out". On a
        // cold process (the periodic tick after Android kills the app in the background, common on
        // slow devices) this Loading window includes Hilt/Supabase-client/Room cold-start, not just
        // the SDK's local-storage session read — 2.5s was too tight and was hard-masking logged-in
        // users (bug found 2026-07-14). Glance widget updates run with a generous WorkManager-backed
        // budget, so 8s is safe; never hang forever regardless.
        val status = withTimeoutOrNull(TIMEOUT_MS) {
            ep.authRepository().status.first { it !is AuthStatus.Loading }
        } ?: AuthStatus.Unauthenticated
        val hasSession = status is AuthStatus.Authenticated

        provideContent {
            val prefs = currentState<Preferences>()
            val userToggled = prefs[USER_TOGGLED_KEY] ?: false
            val display = resolveWidgetDisplay(
                netAssets = netAssets,
                symbol = symbol,
                hasSession = hasSession,
                isPinSet = appLock.isPinSet,
                isLocked = isLocked,
                globalHide = globalHide,
                userToggled = userToggled,
            )
            GlanceTheme { BalanceWidgetContent(display) }
        }
    }

    companion object {
        val USER_TOGGLED_KEY = booleanPreferencesKey("balance_widget_user_toggled")
        private const val TIMEOUT_MS = 8000L
    }
}

/** Hilt bridge for a Glance widget (which is not itself an injectable component). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun netAssetsUseCase(): ObserveNetAssetsUseCase
    fun currencySymbolUseCase(): ObserveCurrencySymbolUseCase
    fun privacyModeUseCase(): ObservePrivacyModeUseCase
    fun appLockUseCase(): ObserveAppLockUseCase
    fun authRepository(): AuthRepository
    fun appLockManager(): AppLockManager
}

@Composable
private fun BalanceWidgetContent(display: WidgetDisplay) {
    val context = LocalContext.current
    val amountText = when (display) {
        WidgetDisplay.HardMasked -> MASKED
        is WidgetDisplay.Soft -> display.text ?: MASKED
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_heart),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(14.dp),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = "Net assets",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = "Refresh",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier
                    .size(18.dp)
                    .clickable(actionRunCallback<RefreshBalanceWidgetAction>()),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = amountText,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (display is WidgetDisplay.Soft) {
                Spacer(GlanceModifier.width(10.dp))
                Image(
                    provider = ImageProvider(
                        if (display.revealed) R.drawable.ic_widget_eye_off
                        else R.drawable.ic_widget_eye,
                    ),
                    contentDescription = if (display.revealed) "Hide amount" else "Show amount",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier
                        .size(22.dp)
                        .clickable(actionRunCallback<ToggleBalanceRevealAction>()),
                )
            }
        }
    }
}
