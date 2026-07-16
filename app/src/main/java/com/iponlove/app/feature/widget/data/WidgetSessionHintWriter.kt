package com.iponlove.app.feature.widget.data

import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the live auth session state into [WidgetSessionStore] for the whole process lifetime, so
 * the balance widget reads a cached hint instantly instead of blocking on the Supabase SDK's
 * cold-start session read (Item 36). Started once per process from [com.iponlove.app.IponApp]
 * (alongside the couple channel), on the app scope.
 *
 * [AuthStatus.Loading] is transient and never persisted (it must not clobber a known good hint
 * during the launch restore window). A password-recovery session counts as **no** normal session
 * for the widget — it only exists to set a new password (ADR-0027), so it maps to `false`.
 */
@Singleton
class WidgetSessionHintWriter @Inject constructor(
    private val authRepository: AuthRepository,
    private val store: WidgetSessionStore,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            authRepository.status
                .mapNotNull { status ->
                    when (status) {
                        is AuthStatus.Loading -> null
                        is AuthStatus.Authenticated -> true
                        is AuthStatus.Unauthenticated -> false
                        is AuthStatus.PasswordRecovery -> false
                    }
                }
                .distinctUntilChanged()
                .collect { store.set(it) }
        }
    }
}
