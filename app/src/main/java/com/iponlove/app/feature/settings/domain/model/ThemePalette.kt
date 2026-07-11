package com.iponlove.app.feature.settings.domain.model

/**
 * Theme palettes. [isFree] marks the allowlist (§10.1): Rose + Peach ship free; the other four
 * are Premium. Palettes are an *allowlist*, not a count cap or a single boolean [Feature] — the
 * gate is per-swatch.
 */
enum class ThemePalette(val label: String, val seedHex: String, val isFree: Boolean) {
    ROSE("Rose", "#C2647A", isFree = true),
    MAUVE("Mauve", "#9B6B7A", isFree = false),
    LAVENDER("Lavender", "#8B7BB5", isFree = false),
    PEACH("Peach", "#C47A5A", isFree = true),
    SAGE("Sage", "#6B8F71", isFree = false),
    MOCHA("Mocha", "#8B6F5A", isFree = false);

    /**
     * The palette actually applied given the current lock state — the **G8 revert** (§10.1). While
     * [locked] (enforcement ON + no premium), a Premium palette is downgraded to [FREE_DEFAULT];
     * a free palette is untouched. This is a *pure read-time derivation*, never a write, so the
     * user's chosen palette stays intact in storage and **auto-restores** the moment [locked]
     * clears (re-grant / enforcement off) — the non-destructive, flip-day-safe reconciliation.
     */
    fun effective(locked: Boolean): ThemePalette = if (locked && !isFree) FREE_DEFAULT else this

    companion object {
        /** The palette a locked Premium palette reverts to (G8). */
        val FREE_DEFAULT = ROSE
    }
}
