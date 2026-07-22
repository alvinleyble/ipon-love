package com.iponlove.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * The free motif-avatar catalog (v1.6.7 Item 3 Leg 1). A motif is just a **shape** — the avatar
 * takes its color from the user's existing `accent_color` (ADR-0014), so there is no separate
 * avatar color field. All motifs are free; the stored value is a plain [key] string, so a future
 * premium-locked motif is a one-line add.
 *
 * The glyphs reuse the app's `material-icons-extended` set rather than the Material-Symbols SVG
 * plumbing of the icon registry, so [HEART] is byte-for-byte the same `Icons.Filled.Favorite`
 * used by `HeartBullet`, the FAB, and the Couple nav tab — the avatar's default reads as the same
 * heart that already runs through the app.
 */
enum class AvatarMotif(val key: String, val icon: ImageVector, val label: String) {
    HEART("heart", Icons.Filled.Favorite, "Heart"),
    LEAF("leaf", Icons.Filled.Eco, "Leaf"),
    BLOOM("bloom", Icons.Filled.LocalFlorist, "Bloom"),
    SPARKLE("sparkle", Icons.Filled.AutoAwesome, "Sparkle"),
    WAVE("wave", Icons.Filled.Waves, "Wave"),
    CRESCENT("crescent", Icons.Filled.DarkMode, "Crescent"),
    GEM("gem", Icons.Filled.Diamond, "Gem"),
    SPROUT("sprout", Icons.Filled.Grass, "Sprout"),
    STAR("star", Icons.Filled.Star, "Star"),
    SUN("sun", Icons.Filled.WbSunny, "Sun"),
    CLOUD("cloud", Icons.Filled.Cloud, "Cloud"),
    PAWPRINT("pawprint", Icons.Filled.Pets, "Pawprint"),
    LOTUS("lotus", Icons.Filled.Spa, "Lotus"),
    PINWHEEL("pinwheel", Icons.Filled.FilterVintage, "Pinwheel"),
    NOTE("note", Icons.Filled.MusicNote, "Note"),
    TREE("tree", Icons.Filled.Park, "Tree");

    companion object {
        /** The default motif when none is stored (dev-phase, no backfill — [User.avatarMotif] null). */
        val Default = HEART

        /** Null / blank / unrecognized key → the [Default] (Heart). Pure — unit-tested. */
        fun fromKey(key: String?): AvatarMotif =
            entries.firstOrNull { it.key == key } ?: Default
    }
}

/**
 * A motif avatar: an accent-filled leaf-squircle with a white motif glyph centered — the same
 * recipe as the account/category icon squircles ([LeafShapes.IconSquircle]). [accentHex] is the
 * user's stored `accent_color`; a null/invalid value falls back to the palette's Playful accent
 * (with `onAccent` ink) so the avatar always re-tints with the active theme.
 */
@Composable
fun MotifAvatar(
    motifKey: String?,
    accentHex: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val colors = LocalPlayfulColors.current
    val parsed = parseHexColor(accentHex)
    val fill = parsed ?: colors.accent
    // Saturated attribution colors take white ink (matching AccountCard); the Playful-accent
    // fallback uses the palette-correct onAccent so contrast holds in every theme/mode.
    val ink = if (parsed != null) Color.White else colors.onAccent
    Box(
        modifier = modifier.size(size).clip(LeafShapes.IconSquircle).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AvatarMotif.fromKey(motifKey).icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * The motif picker (v1.6.7 Item 3 Leg 1; catalog expanded v1.6.9 Item 42): a wrapped grid of
 * avatars in the user's [accentHex],
 * the [selectedKey] ringed. Live-preview only — the caller persists on tap (mirrors [AccentColorRow]).
 */
@Composable
fun MotifPicker(
    selectedKey: String?,
    accentHex: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val selected = AvatarMotif.fromKey(selectedKey)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvatarMotif.entries.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { motif ->
                    MotifSwatch(
                        motif = motif,
                        accentHex = accentHex,
                        selected = motif == selected,
                        enabled = enabled,
                        onClick = { onSelect(motif.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MotifSwatch(
    motif: AvatarMotif,
    accentHex: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        MotifAvatar(
            motifKey = motif.key,
            accentHex = accentHex,
            size = 52.dp,
            modifier = if (selected) {
                Modifier.border(3.dp, colors.textPrimary, LeafShapes.IconSquircle)
            } else {
                Modifier
            },
        )
        Text(
            text = motif.label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = if (selected) colors.textPrimary else colors.textSecondary,
        )
    }
}
