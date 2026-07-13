package com.iponlove.app.feature.settings.domain.model

/**
 * A cosmetic display-currency symbol (Horizon #14 / V1.6.5 Item 18). **Display-symbol-only** —
 * every amount stays the same underlying value; only the rendered glyph changes. There is no
 * per-account currency and no FX conversion. Chosen per-user (a personal display preference like
 * [ThemePalette]); the combined couple view renders the *viewer's* own choice, never reconciled
 * across the couple. Only symbols with distinct glyphs are offered — since nothing converts, two
 * currencies that share a glyph (USD/SGD/HKD → `$`) would be visually identical.
 */
enum class CurrencySymbol(val glyph: String, val code: String) {
    PHP("₱", "PHP"),
    USD("$", "USD"),
    EUR("€", "EUR"),
    GBP("£", "GBP"),
    JPY("¥", "JPY"),
    KRW("₩", "KRW"),
    INR("₹", "INR"),
    THB("฿", "THB");

    companion object {
        val DEFAULT = PHP
    }
}
