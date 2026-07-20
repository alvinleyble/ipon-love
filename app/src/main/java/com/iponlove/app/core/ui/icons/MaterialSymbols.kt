package com.iponlove.app.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * Builds an [ImageVector] from a Material Symbols Rounded (FILL 1) path string,
 * copied verbatim from google/material-design-icons (`symbols/web/<name>/materialsymbolsrounded/`).
 * Those SVGs use a "0 -960 960 960" viewBox (origin top-left, y in [-960, 0]); the enclosing
 * `group(translationY = 960f)` shifts y into Compose's [0, 960] top-left-origin viewport.
 * `Icon()` tints via ColorFilter regardless of the fill color set here.
 */
internal fun msRounded(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        group(translationY = 960f) {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }
    }.build()
