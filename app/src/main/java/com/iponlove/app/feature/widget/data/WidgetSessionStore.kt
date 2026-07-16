package com.iponlove.app.feature.widget.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * A coarse "is there an active session?" hint for the balance widget, written at every [AuthStatus]
 * transition by [WidgetSessionHintWriter] (Item 36). The widget's `provideGlance` reads this instead
 * of blocking on the Supabase SDK's cold-start session read — which on a frozen process took up to
 * 8s and, on timeout, mis-masked a logged-in user as signed out (and hid the eye). DataStore is a
 * fast local file read, so the widget resolves instantly.
 *
 * `null` = never written (fresh install, or the first widget render right after updating to the
 * build that added this before the app is ever reopened). In that one case the widget falls back to
 * a short live probe (see [resolveWidgetSession]); otherwise the persisted boolean is used directly.
 *
 * Not privacy-sensitive (a bare "was signed in" bit, no PII) and self-maintaining — sign-out drives
 * [AuthStatus.Unauthenticated] → `false`, so it never needs clearing in the sign-out wipe.
 *
 * `open` so tests can substitute an in-memory fake without a real DataStore.
 */
open class WidgetSessionStore(
    private val dataStore: DataStore<Preferences>,
) {
    open suspend fun hasSession(): Boolean? =
        dataStore.data.map { it[KEY] }.first()

    open suspend fun set(hasSession: Boolean) {
        dataStore.edit { it[KEY] = hasSession }
    }

    private companion object {
        val KEY = booleanPreferencesKey("widget_has_session")
    }
}
