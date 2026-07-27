# Budget alerts become a three-rung, user-configurable model with a per-budget local mute — no schema change

## Context

v1.7.1 Items 2, 3, 4, grilled together 2026-07-25 (they modify one subsystem, so they were designed as one session). Today `CheckBudgetAlertsUseCase` fires on a hardcoded `THRESHOLDS = listOf(80, 100)` applied to every budget, deduped per month by `BudgetAlertStore` on the key `budgetId:month:threshold`, cleared at month rollover. Alvin wants three things: a user-chosen **warning** threshold (a single slider, 5–100%, applies to all budgets), a second user-chosen **over-budget** alert (110–300%, its own on/off), and a coarse **per-budget mute** so a single noisy budget can be silenced without a slider per row.

Two pieces of prior context constrain this slice:

- **Decision-5 posture (this app):** every existing notification preference (`budgetAlertsEnabled`) is a **local, per-device** DataStore value, not synced. This slice follows that.
- **ADR-0053 (Item 6, the notification inbox — built *before* this slice):** budget alerts are being migrated into a synced inbox whose rows carry a **deterministic per-category id** (originally specified as `budget:{id}:{month}:{threshold}`), create-if-absent, so the same event produced on two devices merges rather than duplicates. That id scheme interacts directly with making the threshold user-configurable, and is amended here (decision 3).

## Decision

1. **One master switch, not two.** The single "Budgets" category switch (the evolved `budgetAlertsEnabled`, surfaced as ADR-0053's per-category switch) *is* the master gate. Items 2–4 add **sliders only** underneath it — no second on/off toggle. When the Budgets switch is off, nothing budget-related fires (inbox row or push); every per-budget mute state is preserved untouched.

2. **Three alert rungs per budget per month.** A budget can raise up to three alerts: **warn** at a user-chosen 5–100% (5% steps, default 80%), **limit** always fixed at exactly 100%, and **over** at a user-chosen 110–300% (10% steps, default 120%). The warn and limit rungs are governed by the master switch; the **over** rung has its own on/off toggle, **default OFF** (a brand-new alert type — opt-in, keeps existing behavior unchanged). Because `over`'s minimum (110%) sits above `limit` (100%), there is no silent gap: 100% always fires `limit`. **Rejected:** a two-rung model where the over-budget slider *replaces* the 100% alert — the 100% "you've officially blown the budget" moment is the single most important one and matches today's behavior.

3. **Alerts are deduped by rung *name*, not by numeric threshold — amending ADR-0053's id.** The inbox/dedup id becomes `budget:{id}:{month}:{slot}` where `slot ∈ {warn, limit, over}`, replacing the numeric `{threshold}`. Each budget fires each rung at most once per month. This is load-bearing for two reasons: (a) the threshold value is now user-mutable mid-month, so a numeric key would re-fire or orphan when the slider moves; (b) with per-device thresholds and a synced inbox, two devices at *different* thresholds (phone warn=60%, web warn=80%) reach for the **same** `warn` slot, so the create-if-absent merge collapses them to one row — the lower threshold simply fires first and the higher device finds the slot filled. **Consequence:** ADR-0053's `budget:{id}:{month}:{threshold}` id must be built as `budget:{id}:{month}:{slot}` from the start; fold this into Item 6's build rather than retrofitting. A rung that has already fired never re-fires when the slider moves; a rung not yet fired *does* fire if the user lowers its threshold below current spend (they asked to be warned earlier).

4. **Warning collapses into limit at 100%.** If the warn slider is dragged to 100%, only `limit` fires — the `warn` rung is suppressed at exactly 100% so two near-identical notifications don't fire at the same instant. Warn fires only strictly below 100%.

5. **Settings are per-device/local.** The warn %, over %, and over on/off are new local DataStore values in `NotificationPreferencesRepository` (mirroring `budgetAlertsEnabled`), not synced. Consistent with every other notification preference; syncing settings would need plumbing that does not exist. The cross-device duplication risk this could create is fully absorbed by the slot-name dedup (decision 3).

6. **Per-budget mute is a local, per-category preference — no synced column, no Room migration.** The mute is a local DataStore set of muted budget *lines*, keyed so it **persists across months** (budgets are per-month rows; a month-scoped mute would force the user to re-mute every month). It is **not** a column on the synced `Budget` entity. **Rejected:** the synced `budgets.notificationsEnabled` column the Item-4 booking anticipated — it would (a) contradict decision 5 (all notification prefs are local), (b) sync a mute to the partner on shared budgets (unwanted, see decision 7), and (c) reset every month with the per-month row. The local approach is simpler *and* more correct, and drops the migration the doc predicted.

7. **Muting a shared budget is per-device.** Because the mute is local (decision 6), muting a couple-owned budget silences only *your* alerts; the partner keeps theirs unless they mute it on their own device. Neither partner can silence the other — consistent with ADR-0004/0005/0011 (the app never lets one partner change what the other sees).

8. **Mute = total silence for that budget.** A muted budget raises none of the three rungs, in neither surface (no inbox row, no OS push) — a full gate, scoped to one budget, matching ADR-0053's "off = silent everywhere" applied per-row. All other budgets are unaffected.

9. **UI.** *Settings → Notifications, Budgets section* (the sectioned screen itself is built by Item 6/ADR-0053): master switch, then an indented warn slider, then an over on/off toggle whose slider appears/activates only when the toggle is on; both sliders grey out (visible, inactive) when the master is off. *Budgets tab*: the mute toggles ("Mute alerts" / "Unmute alerts") live in each row's existing ⋮ overflow menu, with a 🔕 marker on muted rows — no always-visible per-row switch (the row is already dense; muting is rare).

10. **Copy.** warn → title `[Category] at [X]%`, body `You've used [X]% of your [Category] budget.` · limit → `[Category] limit reached` / `You've hit your [Category] budget for this month.` · over → `[Category] way over budget` / `You've spent [X]% of your [Category] budget.` `[Category]` is "Overall Budget" for an overall budget; `[X]` is actual spend %.

## Consequences

- **No schema, Supabase, or Room change** — the whole slice rides existing local-settings plumbing plus a `CheckBudgetAlertsUseCase` change. Model/effort: **Sonnet, medium** (the doc's "maybe Opus" was predicated on the now-eliminated synced column).
- **Build order is fixed: Item 6 (ADR-0053) → this slice.** Decision 3 amends Item 6's dedup id and decision 8 needs the inbox to exist. Build the id as `{slot}` inside Item 6.
- **Tier-1 JVM tests required** (CLAUDE.md testing policy) on `CheckBudgetAlertsUseCase`: the three-rung selection, warn-collapse-at-100 (decision 4), over-as-single-trip-wire, and slot-name dedup are pure threshold logic and the bug-prone part.
- **Build-time micro-decision left open:** if a budget is *already* past the over threshold when the user switches the over rung on mid-month, fire immediately or only on the next crossing? Recommended: mirror `BudgetAlertWorker`'s existing "mark-fired-even-when-suppressed" habit (seed the `over` slot as fired while off, so flipping it on doesn't blast a past crossing) — but it is a one-liner either way and worth an on-device gut-check.
- **ADR-0053 amended** (the `{threshold}` → `{slot}` id) and its glossary entry updated in `CONTEXT.md`.
