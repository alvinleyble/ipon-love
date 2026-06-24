package com.iponlove.app.feature.analysis.presentation.components

import androidx.compose.ui.graphics.Color

/**
 * Fallback palette for chart slices when a category has no stored color (the V1 category
 * editor doesn't set one yet). Distinct, reasonably colorblind-friendly hues; cycled by
 * slice position so the same category keeps the same color within a window.
 */
private val ChartPalette = listOf(
    Color(0xFF5B8DEF),
    Color(0xFFEF767A),
    Color(0xFF49BEAA),
    Color(0xFFF2B134),
    Color(0xFF9B72CF),
    Color(0xFFEC8B5E),
    Color(0xFF4DB6AC),
    Color(0xFFF06292),
    Color(0xFF7986CB),
    Color(0xFFA1887F),
)

/** Parses "#RRGGBB" / "#AARRGGBB" (or named colors); null/blank/invalid -> null. */
fun parseHexColor(hex: String?): Color? =
    hex?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

/** A slice's color: its category's stored color if usable, else a palette color by index. */
fun sliceColor(colorHex: String?, index: Int): Color =
    parseHexColor(colorHex) ?: ChartPalette[index % ChartPalette.size]
