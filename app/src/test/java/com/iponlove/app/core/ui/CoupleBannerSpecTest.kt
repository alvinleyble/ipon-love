package com.iponlove.app.core.ui

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tier-1 tests for the couple-banner derived recipe (v1.7.0 Item 9). Covers the three grilled
 * edge cases: two distinct accents → two-stop gradient; awaiting partner (null accent) → single
 * wash; identical accents → a valid near-solid gradient (not collapsed to a wash). The Compose
 * render is verify-by-running.
 */
class CoupleBannerSpecTest {

    private val blue = Color(0xFF1565C0)
    private val red = Color(0xFFC62828)

    @Test
    fun `two distinct accents produce a two-stop gradient in order`() {
        val spec = bannerBrushSpec(blue, red)

        assertThat(spec).isInstanceOf(BannerBrushSpec.Gradient::class.java)
        assertThat((spec as BannerBrushSpec.Gradient).colors).containsExactly(blue, red).inOrder()
    }

    @Test
    fun `null partner accent produces a single wash of the current user's accent`() {
        val spec = bannerBrushSpec(blue, null)

        assertThat(spec).isInstanceOf(BannerBrushSpec.Wash::class.java)
        assertThat((spec as BannerBrushSpec.Wash).color).isEqualTo(blue)
    }

    @Test
    fun `identical accents stay a valid near-solid gradient, not a wash`() {
        val spec = bannerBrushSpec(blue, blue)

        assertThat(spec).isInstanceOf(BannerBrushSpec.Gradient::class.java)
        assertThat((spec as BannerBrushSpec.Gradient).colors).containsExactly(blue, blue).inOrder()
    }

    // ---- effectiveBannerSource (Item 10) — the freeze/revert correctness for the shared gate ----

    @Test
    fun `unlocked with a url renders the photo`() {
        val source = effectiveBannerSource("https://x/couple-banners/c/1.jpg", unlocked = true)

        assertThat(source).isInstanceOf(BannerSource.Photo::class.java)
        assertThat((source as BannerSource.Photo).url).isEqualTo("https://x/couple-banners/c/1.jpg")
    }

    @Test
    fun `locked with a url reverts to the derived gradient (freeze, non-destructive)`() {
        val source = effectiveBannerSource("https://x/couple-banners/c/1.jpg", unlocked = false)

        assertThat(source).isEqualTo(BannerSource.Derived)
    }

    @Test
    fun `unlocked with no url is derived`() {
        assertThat(effectiveBannerSource(null, unlocked = true)).isEqualTo(BannerSource.Derived)
    }

    @Test
    fun `unlocked with a blank url is derived (never a blank photo)`() {
        assertThat(effectiveBannerSource("  ", unlocked = true)).isEqualTo(BannerSource.Derived)
    }

    @Test
    fun `locked with no url is derived`() {
        assertThat(effectiveBannerSource(null, unlocked = false)).isEqualTo(BannerSource.Derived)
    }
}
