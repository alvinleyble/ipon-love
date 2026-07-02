package com.iponlove.app.core.util

/**
 * Move the element at [from] to index [to], shifting the rest. No-op on out-of-range [from];
 * [to] is clamped into range. Mirrors [com.iponlove.app.navigation.NavConfig.move] for any list,
 * not just the navbar's pin ids — shared by the Manage drag-handle reorder (Categories/Accounts).
 */
fun <T> List<T>.movedTo(from: Int, to: Int): List<T> {
    if (from !in indices) return this
    val clampedTo = to.coerceIn(0, lastIndex)
    if (from == clampedTo) return this
    val mutable = toMutableList()
    mutable.add(clampedTo, mutable.removeAt(from))
    return mutable
}
