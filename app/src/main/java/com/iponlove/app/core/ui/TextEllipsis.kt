package com.iponlove.app.core.ui

/**
 * Truncates a display string (e.g. a partner's nickname) to [max] chars, appending an ellipsis
 * if it was cut. Guards horizontal layouts (chips, headers) against unbounded partner-supplied
 * text — a name is validated on its *own* device but arrives here unbounded (older data, or the
 * cap only applies to the current user's own edits).
 */
fun String.ellipsize(max: Int): String =
    if (length <= max) this else take(max) + "…"
