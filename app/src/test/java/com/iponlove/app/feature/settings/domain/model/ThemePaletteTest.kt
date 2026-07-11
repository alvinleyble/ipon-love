package com.iponlove.app.feature.settings.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The palette allowlist + the **G8 revert** derivation (S9 / §10.1). The load-bearing property is
 * non-destructiveness: `effective()` only ever *downgrades at read* — the chosen palette is never
 * mutated, so an unlock auto-restores it. Free palettes are always themselves; the four Premium
 * palettes downgrade to [ThemePalette.FREE_DEFAULT] only while locked.
 */
class ThemePaletteTest {

    @Test
    fun `only Rose and Peach are free`() {
        val free = ThemePalette.entries.filter { it.isFree }.toSet()
        assertThat(free).isEqualTo(setOf(ThemePalette.ROSE, ThemePalette.PEACH))
    }

    @Test
    fun `the free default is Rose`() {
        assertThat(ThemePalette.FREE_DEFAULT).isEqualTo(ThemePalette.ROSE)
    }

    @Test
    fun `a locked premium palette downgrades to the free default`() {
        assertThat(ThemePalette.MAUVE.effective(locked = true)).isEqualTo(ThemePalette.ROSE)
        assertThat(ThemePalette.LAVENDER.effective(locked = true)).isEqualTo(ThemePalette.ROSE)
        assertThat(ThemePalette.SAGE.effective(locked = true)).isEqualTo(ThemePalette.ROSE)
        assertThat(ThemePalette.MOCHA.effective(locked = true)).isEqualTo(ThemePalette.ROSE)
    }

    @Test
    fun `a free palette is never downgraded even while locked`() {
        assertThat(ThemePalette.ROSE.effective(locked = true)).isEqualTo(ThemePalette.ROSE)
        assertThat(ThemePalette.PEACH.effective(locked = true)).isEqualTo(ThemePalette.PEACH)
    }

    @Test
    fun `unlocking auto-restores the chosen premium palette (non-destructive)`() {
        // The chosen palette is what's passed in; effective() with locked=false returns it verbatim,
        // which is how a re-grant / enforcement-off restores it with no stored state to undo.
        assertThat(ThemePalette.MAUVE.effective(locked = false)).isEqualTo(ThemePalette.MAUVE)
        assertThat(ThemePalette.MOCHA.effective(locked = false)).isEqualTo(ThemePalette.MOCHA)
    }
}
