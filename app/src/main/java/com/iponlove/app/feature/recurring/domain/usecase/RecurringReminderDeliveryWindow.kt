package com.iponlove.app.feature.recurring.domain.usecase

import java.time.LocalTime

/**
 * The daytime delivery window for **self-triggered** recurring reminders (ADR-0056 decision 3).
 *
 * Off-app delivery is a periodic sweep, and deferred jobs are flushed in Doze maintenance
 * windows — so without this gate *"Have you paid for your Rent?"* could buzz at 03:40, a failure
 * mode the app-open trigger can't produce. Outside the window the run does nothing and the next
 * one picks it up; the six-hour period guarantees at least two runs land inside these thirteen
 * hours regardless of phase, so the gate can never starve the reminder (decision 2).
 *
 * Fixed, not user-configurable: a time-range picker is a disproportionate surface, and offering
 * hour controls would imply a precision `PeriodicWorkRequest` does not have. Device-local, with
 * no timezone-travel handling — the window is thirteen hours wide, so a mid-day crossing isn't
 * observable in practice.
 *
 * The gate applies to the **periodic path only** (decision 4). A blanket quiet-hours check inside
 * `RecurringReminderWorker` would also silence a 3am *app-open* reminder — a regression in
 * ADR-0052's shipped behaviour.
 */
object RecurringReminderDeliveryWindow {

    /** Inclusive start of the window. */
    val START: LocalTime = LocalTime.of(8, 0)

    /** Exclusive end of the window — 21:00 itself is already outside. */
    val END: LocalTime = LocalTime.of(21, 0)

    /** Half-open `[START, END)`: true from 08:00:00 through 20:59:59.999. */
    fun isWithinDeliveryWindow(now: LocalTime): Boolean = !now.isBefore(START) && now.isBefore(END)
}
