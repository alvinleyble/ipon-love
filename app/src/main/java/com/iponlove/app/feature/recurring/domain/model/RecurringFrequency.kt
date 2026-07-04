package com.iponlove.app.feature.recurring.domain.model

/**
 * How often a [RecurringRule] repeats. Combined with the rule's `interval` (every N units),
 * so fortnightly = WEEKLY interval 2, quarterly = MONTHLY interval 3, etc.
 *
 * The schema's `recurring_frequency` enum also declares `CUSTOM`; it's reserved for a
 * post-V1 rule type and intentionally not surfaced here — `interval` already covers the
 * "every N" cases V1 needs.
 */
enum class RecurringFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}
