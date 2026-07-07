# Ipon, Love — Domain Context

The shared language of the Ipon, Love couples finance + notes app. This is a glossary, not a spec — it pins down what each term *means* so the code, docs, and conversations agree.

## Language

### Sync

**Sync cursor**:
The pull high-water mark: the max `server_rev` a device has seen, stored per table in DataStore. Pull fetches rows with a greater `server_rev`. (Push uses the Dirty flag, not a cursor.) See ADR-0002.
_Avoid_: last_sync (ambiguous), last_sync_at, watermark

**`server_rev`**:
A server-assigned `bigint` from one global sequence, stamped on every upsert by a trigger. The pull ordering — "what is new to fetch" — kept separate from `updated_at` ("who wins"). Reflects server receipt order, not author edit time.
_Avoid_: revision, version (those imply a per-row counter), sequence

**`updated_at`**:
The client-stamped, offset-corrected wall-clock time of a row's last write. The comparison key for Last-write-wins. Never overridden by a server trigger.
_Avoid_: modified_at, last_modified

**Last-write-wins (LWW)**:
The conflict-resolution rule: when the same row exists on both sides, the version with the greater `updated_at` is kept. Granularity (whole-row vs per-field) is defined in a later decision.
_Avoid_: LWW-by-field (until decided), merge

**Clock offset**:
The delta between this device's wall clock and Supabase server time, captured on each successful sync and applied when stamping `updated_at`, so a skewed device self-corrects toward server time.
_Avoid_: drift, skew (those name the problem, not the stored correction)

**Tombstone**:
A soft-deleted row (`is_deleted = true`) retained — indefinitely in V1 — so the delete propagates through sync. Never hard-deleted (hard-removing one risks resurrection; see ADR-0010). Filtered from all UI queries.
_Avoid_: deleted record, hard delete

**Dirty flag (`pending_sync`)**:
A local-only boolean on every syncable Room row, set on each local write and cleared on a successful push ack. It — not any timestamp — is what selects rows to push. Never sent to Supabase.
_Avoid_: needs_sync, modified flag

**Opening balance**:
The starting balance stored on an Account (`opening_balance`). The only balance figure that syncs; current balance is derived from it plus the ledger, never stored authoritatively. See ADR-0007.
_Avoid_: starting balance, initial balance, balance

**Adjustment transaction**:
A special transaction representing a manual balance correction, so "adjust balance" stays in the ledger rather than overwriting a stored balance. See ADR-0007.
_Avoid_: balance adjustment, correction

### Couples

**Couple**:
The pairing of exactly two Users (user1, user2), identified by a shared invite code and couple name. The unit of data sharing.
_Avoid_: pair, household, family, group

**Invite code**:
The unique code on a Couple that an unpaired user redeems (via the `redeem_invite` RPC) to become user2. Generated/rotated server-side. See ADR-0006.
_Avoid_: pairing code, join code, link

**Combined view**:
The merged, color-coded view of both partners' non-private transactions, plus shared budgets and each partner's monthly spending total. Shows shared *spending*, not partner account balances (which can't be computed correctly past private activity). Distinct from each user's individual view. See ADR-0011.
_Avoid_: shared view, joint view, merged view

**Monthly spending total**:
Per-partner figure in the combined view header (the mockup's chips) = the sum of that partner's *visible* (non-private) transactions for the period. Excludes private activity by design.
_Avoid_: partner balance, net worth, partner total

**Private transaction**:
A transaction marked `is_private = true`. Its content is redacted server-side from partner reads, but its existence + flags still cross the wire so the partner's replica can purge it. See ADR-0005.
_Avoid_: hidden, secret

**Redacting partner view**:
A server-side view (`partner_transactions`, etc.) through which a user reads the partner's rows: always reveals `id`/owner/flags/`server_rev`/`updated_at`, but nulls content columns when the row is private or deleted. The mechanism that lets removals converge. See ADR-0005.
_Avoid_: partner read policy (superseded), shared view

**Purge (local)**:
The client action of deleting a replicated partner row from local Room when it is pulled as a redacted/flagged marker (private, deleted, or unshared). Distinct from a Tombstone, which a user creates for their *own* deletes.
_Avoid_: evict, remove

**Shared record**:
A row owned by the couple rather than one user — a shared budget (`couple_id` set, `user_id` null) or a shared note (`is_shared = true`). Either partner may edit it.
_Avoid_: joint record, couple record (use "shared")

**Conflict copy**:
A new note auto-created when a partner's local edits to a shared note would otherwise be discarded by LWW — the local edits are forked off (title suffixed "(conflict copy — Name)") instead of lost. See ADR-0003.
_Avoid_: conflicted copy, fork, duplicate

**Partner debt**:
A record of money informally owed between the two partners of a Couple — one borrower and one lender. Scoped to the couple (`couple_id`), not owned by a single user. Both partners can create, view, and repay debts. Only exists and is visible when paired; soft-deleted on unpair.
_Avoid_: couple loan, IOU, shared debt

**Partner debt payment**:
A single (partial or full) repayment recorded against a partner debt. Multiple payments accumulate; remaining balance is derived as original amount − sum of non-deleted payments.
_Avoid_: debt repayment, instalment

**Remaining balance (debt)**:
Derived figure = `partner_debt.amount` − `sum(partner_debt_payments.amount)` for non-deleted payments. Never stored — always computed at read time by `PartnerDebtBalanceCalculator` (same derivation pattern as account balance, ADR-0007).
_Avoid_: outstanding amount, balance due

**Shared account**:
An Account owned by the couple rather than one user (`couple_id` set, `user_id` null) — both partners log transactions against it and both see its balance. Distinct from a personal account. Its balance sums *both* partners' transactions, which is only computable because a transaction on a shared account is forced non-private. The ADR-0011 carve-out: shared-account balances *are* shown precisely because their activity is never private. See ADR-0018.
_Avoid_: joint account, couple account (use "shared account")

**Shared category**:
A Category owned by the couple (`couple_id` set, `user_id` null), appearing in both partners' category pickers and usable on any transaction. A couple-owned [[Shared record]], replicated via the base-table pull (not a redacting view). See ADR-0018.
_Avoid_: joint category, global category

**Revert-to-creator**:
What happens to a couple-owned account/category on un-share or unpair: the row becomes the **creator's** personal row (`user_id = created_by`, `couple_id = null`), keeping its history; it is purged from the other partner, whose transactions referencing it fall back to "Unknown account"/"Uncategorized". Keeps unpair unilateral (never blocked). See ADR-0018, amends ADR-0008.
_Avoid_: dissolve, transfer ownership

**Debt netting**:
Collapsing opposing partner debts into the real position. When a debt is created opposite to existing open debt, it auto-offsets oldest-first via linked, auditable **netting payments** (`DebtPayment`s that reference the counter-debt) — the smaller debt closes, the larger reduces. The couple net was always *derived*; netting makes the *records* agree with it. See ADR-0019.
_Avoid_: reconcile, merge debts, cancel

**Paid on behalf**:
A transaction logged with the "Paid for Partner" toggle: it records the normal expense **and** auto-creates a partner debt (borrower = partner) for an *amount owed* (default = full transaction amount). The debt keeps a display-only `source_transaction_id`; the link is fire-and-forget (no cascade on edit/delete). See ADR-0019.
_Avoid_: covered, fronted

**Settlement (debt)**:
Repaying a partner debt as real money movement: two ledger legs — the payor's outflow (their account) and the receiver's inflow (their account, an optional inline affordance on the debt board). Both legs are EXPENSE/INCOME flagged `is_settlement` so balances move but Analysis excludes them. Distinct from a bare [[Partner debt payment]], which need not touch any account. See ADR-0019.
_Avoid_: payback transaction, repayment transfer

### Goals

**Savings goal**:
A target amount a user (or [[Couple]]) is saving toward, e.g. "Japan trip — ₱80,000". Personal by default, optionally a [[Shared record]] via the generic sharing layer. Metadata (name, target, date) is **creator-owned** to avoid an LWW clobber on the target. Its own pinnable module, not a Manage tab. See ADR-0025.
_Avoid_: wishlist, wish, shopping list (a goal is *reached*, not *purchased*)

**Goal contribution**:
An append-only row recording an amount put toward a [[Savings goal]], owned by whoever contributed it. Standalone bookkeeping — in V1 it does **not** move real money out of any account (that is the deferred "savings envelopes" feature). Independent rows never conflict under [[Last-write-wins (LWW)]], unlike a shared mutable counter would. See ADR-0025.
_Avoid_: deposit, payment, saving

**Saved amount**:
The progress of a [[Savings goal]], **derived** as the sum of its non-deleted [[Goal contribution]]s — never a stored field. Same derivation discipline as [[Opening balance]] / account balance (ADR-0007), chosen so concurrent partner contributions can't clobber each other. _reached_ is likewise derived (`saved ≥ target`).
_Avoid_: progress, balance, total saved (as a stored column)

### Transactions

**Transfer fee**:
An optional fee on a `TRANSFER` transaction, represented as a second, linked `EXPENSE` transaction (not a plain field on the transfer row) auto-assigned to a dedicated built-in category so it's groupable in Analysis. Deliberately **not** modeled like a partner-debt settlement leg (ADR-0019) despite the surface similarity — it does **not** carry `is_settlement` (that flag makes Analysis *exclude* a row; a transfer fee must be *included*, since it's real incidental spending, not a repayment), and it **cascades** with its parent transfer (editing the fee amount or deleting the transfer updates/soft-deletes the linked expense too) rather than being fire-and-forget like the debt link — an orphaned fee-expense after its parent transfer is deleted would silently corrupt balance and Analysis totals. See ADR-0031.
_Avoid_: settlement leg, linked debt, transfer expense

**Reset finances**:
A user-initiated "restart fresh" action (password re-authed, in Settings) that soft-deletes only the user's **own** money-movement rows — [[Transaction|transactions]], [[Paused (recurring rule)|recurring rules]], budgets, and goal contributions — in one local transaction, then syncs the tombstones like any other delete. Deliberately keeps *structure* (accounts, categories, savings-goal definitions, opening balances), all notes, and all couple/shared state; balances fall to their [[Opening balance]]. Distinct from a full account deletion (which removes the identity — users row, auth, pairing). See ADR-0037.
_Avoid_: wipe, clear data, factory reset, delete account

### Recurring

**Paused (recurring rule)**:
An indefinite suspension of a recurring rule — no occurrences materialize while paused. Resuming jumps `nextDate` forward to the next occurrence from today; it never backfills whatever was missed during the pause (unlike a plain schedule resume, which would materialize a backlog of backdated transactions). Distinct from deleting a rule, which is permanent. See ADR-0035.
_Avoid_: disabled, inactive, suspended

**Skip (recurring occurrence)**:
A one-shot action that advances a recurring rule's `nextDate` past a single upcoming occurrence without materializing a transaction for it — the rule keeps generating normally afterward. Lighter than [[Paused (recurring rule)|pausing]]: no persisted flag, affects exactly one occurrence. See ADR-0035.
_Avoid_: dismiss, cancel occurrence, delete occurrence

### Budgets

**Rollover (budget)**:
An opt-in, per-budget property (`rolloverEnabled`) under which both unused amount *and* overspend carry forward into the next month, symmetrically — a real running ledger, not a one-way "leftover only" perk. Deliberately has **no floor**: a carried deficit can push the effective limit below ₱0, and the UI must surface that plainly rather than clamping it. The chain **breaks at a gap month** (no budget row at all for that category/month) — carry-forward resets to ₱0 at the next month that has a row, rather than skipping through the gap. See ADR-0036.
_Avoid_: carryover, budget ledger, leftover budget

**Effective limit**:
The actual spending ceiling for a budget in a given month once [[Rollover (budget)]] is applied: `amount + carriedFromPreviousMonth`. Computed at read time by chaining backward through consecutive `yearMonth` rows — never persisted, so changing the rollover rule doesn't require rewriting history. Distinct from the budget's own stored `amount`, which never changes regardless of rollover.
_Avoid_: adjusted budget, actual limit, real budget

### Analysis

**Budget period**:
The window over which income, expense, and budgets are computed — in V1 hard-coded to the **calendar month**. Net is strictly *same-period* (`this-month income − this-month expense`), never a cross-period subtraction; last month's income is shown as a separate context stat so an empty pre-payday month isn't alarming. A **payday-anchored** period (cycle starts on payday) is the planned post-V1 fix that dissolves the empty-before-payday problem at the source.
_Avoid_: month (ambiguous), pay cycle (until built), reporting period

**Analysis period**:
A **steppable calendar bucket** for the Analysis screen's time-range filter — Day, Week, Month, Quarter, Semi-annual, Annual, or All-time — each pageable to the literal previous/next calendar instance via `PeriodStepper` (e.g. Quarter steps between Jan–Mar, Apr–Jun, ...). Deliberately **not** a crypto-app-style trailing window anchored to "now" (there is no "previous 3 months ending today" concept here) — reviewing a specific past period (e.g. "what did March look like") is a real use case for a finance app, unlike a price chart. Labels are kept short for layout reasons only; the short label does not imply trailing-window semantics. All-time's start boundary is a fixed, arbitrarily-early `Instant`, not a query for the actual earliest transaction. See ADR-0030.
_Avoid_: trailing window, rolling period, lookback window

### App shell

**Lock overlay**:
The app lock rendered as an opaque full-screen layer *above* an always-composed app, rather than a branch that replaces it. Preserves navigation state and ViewModels across a lock so in-progress drafts survive; drafts also use `SavedStateHandle` for process death. See ADR-0023.
_Avoid_: lock screen route, lock gate

**Onboarding**:
The first-run-only flow for a brand-new account: value-prop → pair-or-solo → starter-template picker → home (pairing before templates). See ADR-0024.
_Avoid_: setup wizard, intro, tutorial

**New-user gate**:
The condition that triggers [[Onboarding]] and starter seeding: owned categories *and* accounts both empty **after the first sync has successfully completed** — not raw local emptiness (which would duplicate-seed a reinstalling or second-device user). An `onboardingDone` flag only suppresses re-prompting. See ADR-0024.
_Avoid_: first launch, is-new-user flag

**Password recovery**:
A distinct `AuthStatus` state, entered only when the SDK session originates from a password-reset deep link (`session.type == "recovery"`) rather than an ordinary sign-in. Routes to a dedicated "set new password" screen instead of the app shell, and short-circuits the `Authenticated` cascade (sync, onboarding decision, WorkManager enqueues) until the recovery is resolved. Ends in a forced sign-out back to ordinary sign-in, never an auto-continue into the app. See ADR-0027.
_Avoid_: recovery session, reset gate

**PIN lockout**:
A flat 5-wrong-attempt threshold on the PIN path, followed by a 30-second timed cooldown. The counter persists in DataStore (survives a force-kill) and resets only on a successful unlock, never on elapsed time alone. "Forgot PIN" (email+password re-auth) stays available throughout as an opt-in escape hatch — the lockout never forces it. Biometric's OS-level lockout (`ERROR_LOCKOUT`/`ERROR_LOCKOUT_PERMANENT`) is surfaced with a message rather than silently falling back to the PIN pad. See ADR-0028.
_Avoid_: PIN throttling, brute-force protection

### Premium & gating

**Entitlement**:
Whether a user currently holds Premium, carried as `is_premium` + `premium_until` on the synced [[Couple|users]] row (one-time purchase ⇒ `premium_until = null`). The column is a **client-maintained cache of that user's own Play Billing state**, reconciled on every foreground `queryPurchasesAsync`. Trust is **asymmetric**: for your *own* entitlement Play Billing is authoritative (the column only mirrors it); for your *partner's*, the column is authoritative and trusted unconditionally, since a device cannot query the other Play account. No server verification in V1 (client-side only) — a rooted client can self-assert, and because shared surfaces unlock on `me.active || partner.active`, one partner's spoof unlocks the joint features for both. Accepted as bounded because every gated item is cosmetic / a [[Cap count]] / ad-removal with **zero server cost**. **Known prerequisite:** the first server-cost feature (AI companion) must add Play RTDN → server-side purchase verification *before* it can gate on entitlement; Alvin intends to add AI eventually, so this is a scheduled, not hypothetical, addition. See ADR-0044.
_Avoid_: subscription status, license, premium flag (as the source of truth — Play is)

**Enforcement**:
The global master switch for the entire paywall (`enforcement_enabled`, a remote `app_config` row cached in Room), orthogonal to per-user [[Entitlement]]. OFF ⇒ every gate is inert and *all* users are fully unlocked regardless of entitlement — the app ships this way (dormant infra, kill-switch OFF). Flipping it ON (Alvin's explicit go, no app release) wakes the gates: `shouldLock(feature) = enforcement_enabled && !hasAccess(feature)`. Cold-start is **fail-open**: a device that does not yet know enforcement is ON (fresh offline install, no sync) treats it as OFF and stays unlocked, self-healing on first foreground sync — so a paying customer reinstalling offline is never wrongly locked.
_Avoid_: paywall flag, feature flag (too generic), gating switch

**Effective access**:
Whether a gate lets an action through. Resolved from the ownership of the **specific row being gated**, not from a per-feature constant: a row attached to a couple-owned entity (`couple_id` set) is a **shared surface** → unlocked when `me.active || partner.active` (D1); a row attached to a single user, or a feature attached to no entity at all (calculator, palettes, no-ads, deep history), is **individual** → `me.active`. Consequence: the same feature can resolve differently per instance — [[Rollover (budget)]] on a personal budget is individual, on the shared couple budget is shared. The §10.1 individual/shared tags are common-case defaults; ownership of the actual row is the rule.
_Avoid_: has-access, is-unlocked (as if global), per-feature scope

**Freeze**:
The lapse / over-cap policy for count-capped entities: rows beyond a free [[Cap count]] stay **visible and read-only** and are never deleted or hidden — only *new* creation past the cap is blocked (block-on-create, not block-on-exceed). [[Entitlement]] is a **pure client-side advisory layer**: it never gates sync, replication, or visibility, and caps are enforced best-effort at create-time in UseCases, never in Postgres — so concurrent cross-device creates can transiently exceed a cap, which freeze tolerates. Cosmetic state (theme palette) has no read-only form, so its one non-freeze rule is **revert-to-free-default**: on any [[Entitlement]]/[[Enforcement]] change the active palette is re-checked against [[Effective access]] and, if now locked, swapped to a free default — non-destructively (the chosen palette is remembered and auto-restores on re-unlock). Its main trigger is enforcement flip-day (every free user on a premium palette), not just refund. An unpair that pushes a user over a free cap (via [[Revert-to-creator]]) resolves as ordinary freeze.
_Avoid_: lock, downgrade, revoke, read-only mode

**Premium grant**:
Comped [[Entitlement]] set server-side (a remote per-user override), distinct from a Play purchase — used for beta-tester comps (Alvin, Patty, `testdev2-5`) and as the primary "unlocked-path" test lane. Written straight to the user's synced row (`is_premium = true`, `premium_until = null` or a beta-end date, `entitlement_source = GRANT`) so it propagates to the partner and unlocks the couple's shared surfaces like any purchase. Because it has no Play purchase behind it, the foreground Play-reconcile loop **skips `GRANT` rows** — the one case where the device does *not* defer to Play, so a grant is never wiped by `queryPurchasesAsync` returning `NOT_OWNED`. Revoked by flipping the row back to `NONE`, after which normal Play reconciliation resumes.
_Avoid_: comp, override flag, manual premium

**Cap count**:
The number of a given entity that counts against its free/premium limit. Defined uniformly as **all non-deleted rows of that entity** in the relevant scope (the user, or the [[Couple]] for shared entities) — **archived rows included**. An archived [[Shared account|account]], [[Shared category|category]], or [[Savings goal]] still counts, because it still carries value (its balance and its transactions remain in [[Analysis period|Analysis]]; the record persists). Only a [[Tombstone|soft-delete]] frees a slot — there is no un-delete affordance, so the count is ungameable. **Sole exception**: [[Partner debt]] counts un-settled entries only (a settled debt is a completed obligation, not held against the cap; see [[Settlement (debt)]]).
_Avoid_: usage, quota, active count, slot

### Beta testing

**Version-mismatch gate**:
A hard, non-dismissable block shown to beta testers whenever their installed `versionCode` doesn't **exactly** match the single row in `app_release_info` (a manually-updated, public-read Supabase table) — exact-match rather than a "not behind" floor, because the goal is every tester on the identical build for comparable bug reports, not merely "not outdated." Checked on the same foreground/resume cadence as `AppLock`. Fails **open** (lets the user in) if the check itself can't complete — never blocks the app over a failed network call. Beta-only (`BuildConfig.IS_BETA_BUILD`). See ADR-0029.
_Avoid_: force update, version check

**Module graph**:
The per-module nested `NavGraph` every pinnable module (Records, Analysis, Manage, Couple, Settings, Calculator, Savings) is wrapped in, uniformly — even ones with only a single screen today. Switching tabs saves/restores each module's own back stack independently, so leaving a module mid-flow (e.g. Records → Recurring) and returning later resumes exactly there, not at the module's root. Add/Edit Transaction is a deliberate exception: a standalone route outside every module graph, since it's reached from the global ⊕ button as well as from inside Records. See ADR-0033.
_Avoid_: tab graph, nested graph (ambiguous with Android's generic term), sub-graph

**Reset-to-root**:
The behavior when re-tapping the bottom-nav tab for the module you're already inside: pops that module's own back stack to its start destination, discarding any pushed sub-screen. Scoped to the back stack only — it does not reset scroll position, viewed month/period, or any other in-screen state. See ADR-0033.
_Avoid_: reset tab, tab reset, go home

**Coach mark**:
A single step of the first-run tutorial: an anchored tooltip pointing at one real, on-screen UI target, advancing only when the user taps that actual target (not a generic "Next" button) — teaches muscle memory rather than playing a slideshow. Deliberately not a dimmed-cutout "spotlight" effect. The generic engine (target highlighting, tooltip rendering, step sequencing) lives in `core/ui/CoachMark.kt`; the Ipon-specific step script lives in `feature/tutorial/`. See ADR-0034.
_Avoid_: spotlight, walkthrough step, tooltip tour

**Tutorial gate (`tutorial_seen`)**:
A local-only DataStore flag, independent of [[New-user gate]]/`isOnboardingDone`, that fires the first-run coach-mark tutorial once per local install. Naturally re-arms whenever local storage is cleared (covers both a brand-new user and an existing user who reinstalled/wiped storage, with one mechanism) — the opposite of onboarding's gate, which is deliberately keyed off synced server state to *avoid* re-triggering for a returning user. A manual "Replay tutorial" entry point in Settings re-runs the sequence without touching this flag. See ADR-0034.
_Avoid_: onboarding flag, tutorial flag, has_seen_tutorial
