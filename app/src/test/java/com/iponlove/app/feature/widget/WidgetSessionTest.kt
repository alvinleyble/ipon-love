package com.iponlove.app.feature.widget

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.widget.data.WidgetSessionResolution
import com.iponlove.app.feature.widget.data.resolveWidgetSession
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The balance widget's session resolution (Item 36): a persisted hint is used directly and never
 * blocks; only a never-written hint falls back to a live probe, and only a *real* probe answer is
 * seeded (a timeout displays fail-closed but persists nothing, so a slow cold start can't bake a
 * wrong "signed out" hint).
 */
class WidgetSessionTest {

    @Test fun `present hint true is used directly and seeds nothing`() = runTest {
        val result = resolveWidgetSession(hint = true) { error("probe must not run when hint present") }
        assertThat(result).isEqualTo(WidgetSessionResolution(hasSession = true, seedHint = null))
    }

    @Test fun `present hint false is used directly and seeds nothing`() = runTest {
        val result = resolveWidgetSession(hint = false) { error("probe must not run when hint present") }
        assertThat(result).isEqualTo(WidgetSessionResolution(hasSession = false, seedHint = null))
    }

    @Test fun `null hint probes and seeds a real true answer`() = runTest {
        val result = resolveWidgetSession(hint = null) { true }
        assertThat(result).isEqualTo(WidgetSessionResolution(hasSession = true, seedHint = true))
    }

    @Test fun `null hint probes and seeds a real false answer`() = runTest {
        val result = resolveWidgetSession(hint = null) { false }
        assertThat(result).isEqualTo(WidgetSessionResolution(hasSession = false, seedHint = false))
    }

    @Test fun `null hint with an unknown (timed-out) probe displays fail-closed and seeds nothing`() = runTest {
        val result = resolveWidgetSession(hint = null) { null }
        assertThat(result).isEqualTo(WidgetSessionResolution(hasSession = false, seedHint = null))
    }
}
