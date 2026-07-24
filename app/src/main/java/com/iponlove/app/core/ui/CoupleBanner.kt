package com.iponlove.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
 * Whether the small couple-photo box shows on top of the hero right now (v1.7.0 Item 10). The
 * gradient always fills the whole hero — the photo, when present, is a small accent layered on
 * top, never a replacement for it. Access-gated: **only when the couple actually has access** —
 * the render is derived at read time, never written, so freeze/re-grant is non-destructive
 * (decision 5): a lapsed couple reverts to [Derived] and a re-grant auto-restores [Photo] with no
 * re-upload.
 */
sealed interface BannerSource {
    data class Photo(val url: String) : BannerSource
    data object Derived : BannerSource
}

/**
 * The effective photo layer: the uploaded [Photo] iff the couple is [unlocked] *and* a non-blank
 * [bannerUrl] is set; otherwise [Derived] (no photo shown). Pure + unit-tested — this is the
 * load-bearing freeze/revert correctness for the shared soft gate.
 */
fun effectiveBannerSource(bannerUrl: String?, unlocked: Boolean): BannerSource =
    if (unlocked && !bannerUrl.isNullOrBlank()) BannerSource.Photo(bannerUrl) else BannerSource.Derived

/**
 * The couple identity hero (v1.7.0 Item 9): a ~120dp strip filled with a two-tone horizontal
 * gradient blending both partners' `accent_color`s ([bannerBrushSpec]), with both [MotifAvatar]s
 * (white-ringed so they read against the same-hue gradient) and the [coupleName] overlaid in white.
 *
 * Zero-config and always-on — the free default identity. Awaiting a partner ([hasPartner] false),
 * it shows a single-accent wash with only the current user's avatar. Re-tints with each user's
 * stored accent, not a palette token, so it stays keyed to *this couple* across theme/mode changes.
 *
 * A premium uploaded photo (Item 10) never replaces this identity content — it floats as a
 * circle straddling the hero's *top edge*, half outside the card and half overlapping in (a
 * cover-photo/profile-pic pattern), so the gradient stays the dominant color and the photo — whose
 * colors are the couple's own and uncontrolled — reads as a deliberate accent rather than a patch
 * fighting the gradient for the same space. [CouplePhotoOverlap] is the exposed "how far above the
 * card" measurement a caller (the camera affordance) needs to stay clear of it.
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
    // Item 10: the premium photo overlaps the hero's top edge when [bannerUnlocked]; null/locked → gradient only.
    bannerUrl: String? = null,
    bannerUnlocked: Boolean = false,
    // Tapping the photo circle (e.g. to open a full-screen viewer); no-op when there's no photo.
    onPhotoClick: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPlayfulColors.current
    // The current user always has an accent; fall back to the palette accent if it's missing/invalid
    // (mirrors MotifAvatar) so the wash/gradient is never built from a null.
    val accentA = parseHexColor(currentAccentHex) ?: colors.accent
    val accentB = if (hasPartner) parseHexColor(partnerAccentHex) else null
    val brush = bannerBrushSpec(accentA, accentB).toBrush()
    val source = effectiveBannerSource(bannerUrl, bannerUnlocked)
    val photoActive = source is BannerSource.Photo

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The card is pushed down by half the photo's height, leaving room above it
                // (outside the clip) for the circle's top half to float in free space.
                .then(if (photoActive) Modifier.padding(top = CouplePhotoOverlap) else Modifier)
                .clip(LeafShapes.Card)
                // The gradient fills the whole card so a [footer] spend strip reads as a *darkened
                // continuation* of the same identity gradient (Item 9 Slice B), not a separate block.
                .background(brush),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Extra height when a photo overlaps the top: the avatars/name block needs to
                    // clear the circle's bottom half before it can start.
                    .height(if (photoActive) height + CouplePhotoOverlap + 20.dp else height),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                        top = if (photoActive) CouplePhotoOverlap + 20.dp else 16.dp,
                    ),
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
        if (source is BannerSource.Photo) {
            AsyncImage(
                model = source.url,
                contentDescription = "Couple photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(CouplePhotoSize)
                    .clip(CircleShape)
                    .then(if (onPhotoClick != null) Modifier.clickable(onClick = onPhotoClick) else Modifier)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

/** The couple photo's diameter (Item 10) — a circle straddling the hero's top edge. */
val CouplePhotoSize: Dp = 144.dp

/** Half of [CouplePhotoSize]: how far the photo pokes above the card's top edge, and how much extra
 *  top clearance the hero content (or an overlaid camera affordance) needs when a photo is active. */
val CouplePhotoOverlap: Dp = CouplePhotoSize / 2
