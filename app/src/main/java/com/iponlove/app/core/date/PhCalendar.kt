package com.iponlove.app.core.date

import java.time.ZoneId

/**
 * The PH-local calendar every *date judgement* keys on, as opposed to the device zone the app
 * *displays* in ([com.iponlove.app.core.ui.formatShortDate]).
 *
 * Frozen by [cross-platform-contract.md](../../../../../../../docs/web/cross-platform-contract.md)
 * §4: "`Asia/Manila` semantics (i.e. the user's PH-local calendar), not the browser's zone and not
 * UTC" — a client in another zone must sort a `2026-07-31T23:30+08:00` expense into the same day
 * and month the phone does, or the two disagree with no row-level conflict to detect.
 *
 * Used by the receipt scanner's date bound (not in the future, not older than 18 months) and its
 * duplicate-scan window (±1 calendar day) — ADR-0062.
 */
val PH_ZONE: ZoneId = ZoneId.of("Asia/Manila")
