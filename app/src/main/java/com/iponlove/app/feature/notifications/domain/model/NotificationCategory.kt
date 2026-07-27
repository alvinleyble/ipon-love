package com.iponlove.app.feature.notifications.domain.model

/**
 * The inbox's notification categories. Each known category maps 1:1 to one switch on
 * Settings → Notifications, and to one OS notification channel, so a user can silence a
 * whole class of notification in either place (ADR-0053 decision 5).
 *
 * [key] is the wire/DB value — **never change a shipped key**, it is embedded in synced rows.
 *
 * [OTHER] is the tolerant fallback for a category this build doesn't know: the coming web app
 * may write a category before Android ships support for it. Such a row still renders in the
 * inbox (never silently dropped) — it simply isn't gated by any per-category switch.
 */
enum class NotificationCategory(val key: String) {
    BUDGET("budget"),
    RECURRING("recurring"),
    COUPLE("couple"),
    OTHER("other"),
    ;

    companion object {
        fun fromKey(key: String): NotificationCategory =
            entries.firstOrNull { it.key == key } ?: OTHER
    }
}
