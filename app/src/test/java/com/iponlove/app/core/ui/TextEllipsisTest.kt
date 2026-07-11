package com.iponlove.app.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextEllipsisTest {

    @Test
    fun shortStringUnchanged() {
        assertThat("Patty".ellipsize(15)).isEqualTo("Patty")
    }

    @Test
    fun exactlyAtMaxUnchanged() {
        assertThat("a".repeat(15).ellipsize(15)).isEqualTo("a".repeat(15))
    }

    @Test
    fun overMaxTruncatedWithEllipsis() {
        assertThat("a".repeat(20).ellipsize(15)).isEqualTo("a".repeat(15) + "…")
    }
}
