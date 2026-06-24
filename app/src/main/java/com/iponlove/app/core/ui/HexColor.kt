package com.iponlove.app.core.ui

import androidx.compose.ui.graphics.Color

/** Parses "#RRGGBB" / "#AARRGGBB" (or a named color); null/blank/invalid -> null. */
fun parseHexColor(hex: String?): Color? =
    hex?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
