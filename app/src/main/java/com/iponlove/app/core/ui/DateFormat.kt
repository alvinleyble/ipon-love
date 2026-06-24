package com.iponlove.app.core.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Short, locale-friendly transaction date, e.g. `Jun 24`. */
private val shortDate = DateTimeFormatter.ofPattern("MMM d")

fun formatShortDate(instant: Instant): String =
    shortDate.format(instant.atZone(ZoneId.systemDefault()))
