package com.iponlove.app.feature.widget

import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.widget.data.WidgetSessionHintWriter
import com.iponlove.app.feature.widget.data.WidgetSessionStore
import com.iponlove.app.feature.widget.presentation.WidgetRefresher
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The hint writer mirrors auth state into the fast widget hint AND self-heals the widget (Item 10):
 * whenever the session becomes present — including a cold process the widget host revived to draw
 * itself, where MainActivity never ran to repaint — it repaints so the NotReady placeholder swaps
 * for real data. Transient [AuthStatus.Loading] must not repaint (it would flash before data).
 */
class WidgetSessionHintWriterTest {

    private val store = mockk<WidgetSessionStore>(relaxed = true)
    private val refresher = mockk<WidgetRefresher>(relaxed = true)

    private fun writerFor(vararg statuses: AuthStatus): WidgetSessionHintWriter {
        val auth = mockk<AuthRepository>()
        every { auth.status } returns flowOf(*statuses)
        return WidgetSessionHintWriter(auth, store, refresher)
    }

    @Test
    fun `session becoming authenticated repaints the widget once`() = runTest {
        writerFor(AuthStatus.Loading, AuthStatus.Authenticated("u1")).start(this)
        advanceUntilIdle()

        coVerify(exactly = 1) { store.set(true) }
        coVerify(exactly = 1) { refresher.refresh() }
    }

    @Test
    fun `signed out does not repaint (nothing to show)`() = runTest {
        writerFor(AuthStatus.Loading, AuthStatus.Unauthenticated).start(this)
        advanceUntilIdle()

        coVerify(exactly = 1) { store.set(false) }
        coVerify(exactly = 0) { refresher.refresh() }
    }

    @Test
    fun `password-recovery session is not a real session and does not repaint`() = runTest {
        writerFor(AuthStatus.PasswordRecovery("u1")).start(this)
        advanceUntilIdle()

        coVerify(exactly = 1) { store.set(false) }
        coVerify(exactly = 0) { refresher.refresh() }
    }
}
