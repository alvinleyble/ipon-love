package com.iponlove.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * The derived recipe for the couple identity banner (v1.7.0 Item 9, the free default layer):
 * how the two partners' `accent_color`s combine into the hero fill. Pure + unit-tested — the
 * Compose render ([CoupleBanner]) is verify-by-running.
 *
 * A future premium uploaded photo (Item 10) is a separate layer that *overrides* this slot; this
 * spec is the always-available fallback a free/lapsed couple sees.
 */
sealed interface BannerBrushSpec {
    /** Two-stop blend of both partners' accents. Identical accents render as a valid near-solid. */
    data class Gradient(val colors: List<Color>) : BannerBrushSpec

    /** A single-accent wash — used when the partner's accent isn't known yet (awaiting partner). */
    data class Wash(val color: Color) : BannerBrushSpec
}

/**
 * Derives the banner fill from the two accents. [accentA] is the current user's (already
 * fallback-resolved by the caller to a non-null palette color); [accentB] is the partner's, or
 * `null` while awaiting a partner. Both known → a two-stop gradient (identical values still make a
 * valid gradient that reads as a near-solid); partner absent → a single-accent wash.
 */
fun bannerBrushSpec(accentA: Color, accentB: Color?): BannerBrushSpec =
    when (accentB) {
        null -> BannerBrushSpec.Wash(accentA)
        else -> BannerBrushSpec.Gradient(listOf(accentA, accentB))
    }

/** The Compose [Brush] for a spec: a horizontal gradient for two accents, a solid fill for a wash. */
fun BannerBrushSpec.toBrush(): Brush = when (this) {
    is BannerBrushSpec.Gradient -> Brush.horizontalGradient(colors)
    is BannerBrushSpec.Wash -> SolidColor(color)
}

/**
 * The couple identity hero (v1.7.0 Item 9): a ~120dp strip filled with a two-tone horizontal
 * gradient blending both partners' `accent_color`s ([bannerBrushSpec]), with both [MotifAvatar]s
 * (white-ringed so they read against the same-hue gradient) and the [coupleName] overlaid in white.
 *
 * Zero-config and always-on — the free default identity. Awaiting a partner ([hasPartner] false),
 * it shows a single-accent wash with only the current user's avatar. Re-tints with each user's
 * stored accent, not a palette token, so it stays keyed to *this couple* across theme/mode changes.
 *
 * Both surfaces share this one hero (v1.7.0 Item 9): Settings → Couple renders it bare (Slice A),
 * while the Combined view attaches a spend strip via [footer] (Slice B, layout "C") — kept in the
 * same clipped card so the gradient stays a single stable identity across both places.
 */
@Composable
fun CoupleBanner(
    coupleName: String,
    currentMotifKey: String?,
    currentAccentHex: String?,
    partnerMotifKey: String?,
    partnerAccentHex: String?,
    hasPartner: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    footer: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPlayfulColors.current
    // The current user always has an accent; fall back to the palette accent if it's missing/invalid
    // (mirrors MotifAvatar) so the wash/gradient is never built from a null.
    val accentA = parseHexColor(currentAccentHex) ?: colors.accent
    val accentB = if (hasPartner) parseHexColor(partnerAccentHex) else null
    val brush = bannerBrushSpec(accentA, accentB).toBrush()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LeafShapes.Card)
            // The gradient fills the whole card so a [footer] spend strip reads as a *darkened
            // continuation* of the same identity gradient (Item 9 Slice B), not a separate block.
            .background(brush),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MotifAvatar(
                        motifKey = currentMotifKey,
                        accentHex = currentAccentHex,
                        size = 48.dp,
                        modifier = Modifier.border(2.dp, Color.White, LeafShapes.IconSquircle),
                    )
                    if (hasPartner) {
                        MotifAvatar(
                            motifKey = partnerMotifKey,
                            accentHex = partnerAccentHex,
                            size = 48.dp,
                            modifier = Modifier.border(2.dp, Color.White, LeafShapes.IconSquircle),
                        )
                    }
                }
                Text(
                    text = coupleName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        footer?.invoke()
    }
}
