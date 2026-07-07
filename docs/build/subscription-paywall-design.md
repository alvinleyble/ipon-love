# Premium Paywall + Feature Gating — Design Doc

**Status: RESOLVED / build-ready (2026-07-08).** Grilled to resolution over five passes; **§9 (D1–D8), §10 (build spec), §11 (G1–G10 mechanism seams), and ADR-0044 are authoritative.** §0–§8 are the grill trail — kept for rationale, but their "open question" phrasing is superseded by §9–§11 (resolved sections carry a pointer banner). The fifth pass (§11) closes the last open lever (ads — **dropped**) and pins the entitlement mechanism (write/reconcile, cold-start fail-open, cap-counting, per-instance scope, beta-comp coexistence) → **ADR-0044**. Domain terms live in `CONTEXT.md` (`Entitlement`, `Enforcement`, `Freeze`, `Effective access`, `Cap count`, `Premium grant`). **Next step: the dormant-infra build — ordered slice plan + resume instructions in §12.** Post-V1 Horizon #15 in `project-build-progress.md`.

**Model = one-time ₱249 in-app purchase for Premium** (not a subscription — pivoted to subscription 2026-07-06, reverted to one-time 2026-07-07; D7). **Generous free** — recording your own money is never gated (D5).

### Decisions at a glance (authoritative TL;DR)
- **D1 couples** — either partner's purchase unlocks all *shared* features for both; individual features stay per-user.
- **D2 propagation** — entitlement = `is_premium`/`premium_until` on the synced `users` row, read across the couple via the redacting view.
- **D3 config** — hardcoded default caps + a Supabase remote-override row (kill-switch, cap tuning, beta grants); enforcement ships **OFF**.
- **D4 trust** — client-side `queryPurchasesAsync` only; no server/RTDN now (added later for AI).
- **D5 free floor** — generous free; own-money tracking + couple pairing never gated.
- **D6** — widgets + budget-alert notifications free.
- **D7 billing** — one-time ₱249 (`INAPP`); no grace/lapse — entitlement is `OWNED`/`NOT_OWNED`, revoked only on refund.
- **Lapse (T1)** — freeze: over-cap data stays read-only, block-on-create, never deleted.
- **Free/premium map** — §8.1 (rationale) → **§10.1 is the locked `Feature`/`PlanLimits` contract.**
- **D8 AI monetization** — separate add-on (not in ₱249 Premium): starter credits + consumable top-up packs + BYOK, **server-metered** via a Supabase Edge Function; deferred Horizon #3.

**Model/effort for building:** Opus for the architecture (offline-first entitlement + couples gating is cross-ADR); Sonnet for mechanical gate placement once the pattern is locked.

---

## 0. The two decisions people conflate — keep them separate

1. **Infrastructure decision** — build the entitlement + gating layer. Cross-cutting, cheap now, expensive to retrofit.
2. **Enforcement decision** — actually flip the paywall on and charge.

**Working stance (to grill):** build the infrastructure *next*, before most remaining Horizon items, but ship it in **"everything unlocked / kill-switch off"** mode. Do not enforce until beta is over and the paid feature set justifies a price. Rationale: half the candidate paid features are unbuilt Horizon items (custom fonts, extra palettes, AI) — if the gating layer exists first, each is *born gate-aware* instead of retrofitted.

> **Q0.1** Do we agree infrastructure-first / enforcement-later? Or is there a reason to defer even the infrastructure (e.g. tier lines so undecided that the `Feature` enum would churn)?
> **Q0.2** What is the kill switch? A remote-config flag (Supabase row) so we can flip enforcement without a release? Or a build-flavor constant?

---

## 1. The model: entitlement + gates

> **RESOLVED — kept as rationale.** Answers live in §9–§10: entitlement location/caching → **D2** (synced `users` row, *not* DataStore — supersedes §1.1's sketch); single choke point + `Feature`/`PlanLimits` → **§10.1** (Q1.5); tier is a **boolean** free/premium (Q1.6, D7); trust model → **D4** (Q1.3/Q1.4). Text below is the reasoning that produced them.

Two separable pieces:

- **Entitlement** — "is this user currently subscribed, and to what?" A single source of truth, cached locally.
- **Gates** — checks at each feature's entry point that consult the entitlement.

### 1.1 Entitlement source of truth (THE offline-first problem)

The app is offline-first (Room is read first, Supabase is background sync). We **cannot** block on a live Google Play Billing call at every feature use.

Sketch to grill:
- `SubscriptionStatus(tier, isActive, expiresAt, inGracePeriod, source, checkedAt)` cached in **DataStore or a Room row**.
- Refreshed by `queryPurchasesAsync` (Play Billing Library) on **app foreground** (same trigger as existing sync — ADR-0002/0012) plus a **WorkManager periodic re-validation** so a cancelled sub doesn't stay "active" forever on a device that rarely reconnects.
- Reads are always local and instant; the network only *refreshes* the cache.

> **Q1.1** Where does entitlement live — DataStore (simple, like theme prefs) or a Room `subscription` row (queryable, joinable, syncable)? Does it need to sync across the couple at all, or is it strictly per-device/per-Play-account?
> **Q1.2** How stale is acceptable? If offline for N days, when does a lapsed subscriber lose access? Grace window before we re-lock? (Play already has a billing-retry grace period — do we lean on that, or add our own on top?)
> **Q1.3** Does the server (Supabase) ever need to know entitlement? Play Billing is client-authoritative, but if any *server-side* feature is gated (e.g. a future AI proxy), we need Play's Real-time Developer Notifications (RTDN) → server verification. Is anything server-gated in scope, or is all gating client-side/cosmetic for now?
> **Q1.4** Trust model: client-side `queryPurchasesAsync` is spoofable on rooted devices. For a PH-market couples app, is that an acceptable risk (yes for cosmetic gates; maybe not if we ever gate something with real server cost like AI)? Where's the line?

### 1.2 Gate types

Three shapes, and features fall into different ones:

- **Hard gate** — blocks the action entirely. Checked in the **UseCase** (which already owns data access per the Scalability Principle). E.g. `CreateSharedAccountUseCase` refuses beyond the free cap.
- **Soft gate** — feature is *visible and desirable* but tapping routes to the paywall. Checked in the **ViewModel** via a small `FeatureAccess` helper. E.g. locked theme palettes shown greyed with a lock, in-app calculator.
- **Plan limit (count cap)** — not on/off but "N vs unlimited." Modeled as **config values**, not per-feature booleans: `PlanLimits(maxSharedAccounts, maxSharedCategories, maxSavingsGoals, ...)`, where free and paid tiers are different instances. More maintainable than a boolean per count.

> **Q1.5** Do we want one choke point (`EntitlementRepository` + `FeatureAccessUseCase` + `PlanLimits`) that all gates consult, rather than scattered `if (isSubscribed)`? (Strongly leaning yes — mirrors "UseCases own data access.") What's the exact API surface — `featureAccess.isUnlocked(Feature.X)` and `planLimits.maxSavingsGoals`?
> **Q1.6** Is tier a boolean (free/paid) or an enum (free/monthly/annual, or free/plus/pro)? Annual-vs-monthly is usually the *same* entitlement at different prices, not different features — confirm we're single-tier-of-features.

---

## 2. The two genuinely hard, app-specific problems

A generic paywall guide stops at §1. These two are where "excellent" is won or lost.

> **RESOLVED — kept as rationale.** Lapse (§2.1) → **freeze** (T1 / D7): over-cap data stays read-only, block-on-create, never deleted. Couples governance (§2.2) → **either-partner-unlocks-both** (D1), propagated via the synced `users` row (D2, closes Q2.4). The A/B/C/D options below are the trail, not live choices.

### 2.1 The lapse problem — you can't delete a user's financial data

If a subscriber creates **3 savings goals** then lapses to a free cap of **1**, what happens to goals #2 and #3? Options:

- **(A) Read-only / frozen** — they stay visible, count toward history, but can't be edited or contributed to until re-subscribe. Data preserved, gentle.
- **(B) Hidden but retained** — hidden from UI, restored on re-subscribe. Preserves data but confuses ("where did my goal go?").
- **(C) Hard block on *creation* only, grandfather existing** — the cap only stops *new* creation; anything created while subscribed stays fully usable forever. Simplest, most user-friendly, but weakens the paywall (subscribe one month, keep 3 goals forever).
- **(D) Force-pick which to keep** — ugly, hostile. Reject.

This applies to every count cap: shared accounts, shared categories, savings goals, custom themes/fonts already applied, etc. A user whose *active theme* is a paid palette and then lapses — do we revert them to a free palette?

> **Q2.1** Per feature or globally: which lapse policy? My instinct: **(A) read-only freeze** for data-bearing entities (goals, shared accounts), **revert-to-free-default** for cosmetic state (theme/font), **(C) grandfather** only where a freeze would be more confusing than it's worth. Grill each.
> **Q2.2** Does the answer differ between "created while subscribed then lapsed" vs "the free cap was always lower"? (I.e. is there ever a *migration* moment where existing beta users are over the future free cap?) Per the dev-phase memory we don't design backfills for existing users — but our own test/beta data may trip this.

### 2.2 The couples problem — whose entitlement governs shared features?

Alvin subscribes, Patty doesn't (or vice versa). Who governs the **shared** surface — shared accounts, shared categories, shared budget, partner debt tracker, combined view, shared notes/lists?

Options:

- **(A) Either-partner-subscribed unlocks shared features for both** — generous, drives "get your partner to pay." A couple needs only *one* subscription for the joint stuff. Individually-gated features (own themes, own calculator) still per-user.
- **(B) Both must subscribe** — doubles revenue per couple but likely kills adoption (PH market, one person pays for the household).
- **(C) Subscriber sees shared features, non-subscriber sees them read-only** — asymmetric, confusing when both are editing the same shared budget.
- **(D) "Couple subscription" as a first-class concept** — one purchase covers the pair, tied to the `couple` row, not the user. Architecturally heaviest (entitlement syncs across the couple, survives unpair/re-pair, ADR-0006/0008 couple-ops-are-RPCs rules apply).

This interacts hard with:
- **Sync/redaction (ADR-0004/0005/0011)** — a non-subscriber replicating partner rows through redacting views: does gating change what replicates?
- **Unpair purge (ADR-0006/0008)** — if a "couple subscription" exists and they unpair, whose entitlement survives?
- **The pairing flow** — is subscription offered/required at pairing?

> **Q2.3** Which couples model? My instinct: **(A) either-partner unlocks shared features**, individual features stay per-user — cheapest to reason about, best adoption, avoids a "couple subscription" entity. But it means entitlement must be *visible across the couple* (Patty's app needs to know Alvin is subscribed to unlock the shared budget) — which pushes entitlement into synced/redacted space (contradicts Q1.1's "per-device" simplicity). Grill this tension hard.
> **Q2.4** If shared-feature unlock depends on the *partner's* entitlement, how does Patty's offline device learn Alvin subscribed? Through the existing couple sync channel (ADR-0015 realtime broadcast)? A field on the redacted partner view? This is the crux where §1.1 (offline entitlement) and §2.2 (couples) collide.
> **Q2.5** What is the free couples experience? If pairing/combined-view is itself premium, the app's headline feature is behind the wall on day one — probably wrong. If pairing is free and only *limits* (shared account/category counts) are premium, that's gentler. Where's the line?

---

## 3. Play Billing mechanics (lower-risk, still needs decisions)

> **Superseded by D7 (§9):** the model is a **one-time ₱249 in-app purchase**, not a subscription. The subscription-specific mechanics below (base plans, trials, cadence) no longer apply — kept for historical context. Restore/multi-device, testing approach, and the `core/billing` module boundary still hold.

- **Library:** Google Play Billing Library (subscriptions). New `core/billing` module (generic, not feature-buried — Scalability Principle).
- **Restore / multi-device:** "log in on new phone → data restores" is an existing promise (PRD §8). Subscription entitlement is tied to the **Google Play account**, not our Supabase login — so restore is automatic via `queryPurchasesAsync` on the new device *if the same Play account*. Edge case: our app login (Supabase email) and Play account can differ. Grill.
- **Free trial / intro offer:** Play supports intro pricing and free trials natively. In scope for launch?
- **Pricing:** monthly? annual? both? PH-market price points (₱).
- **Testing:** Play Console **license testers** + staging flavor; how do we test locked/unlocked/lapsed/grace states without real charges? (Play's static test responses + test tracks.)

> **Q3.1** New `core/billing` module boundary — what's in it vs. in a `feature/subscription` (paywall screen, manage-subscription UI)?
> **Q3.2** Trial/intro offer at launch, or flat price first?
> **Q3.3** Monthly + annual, or one cadence?
> **Q3.4** How do we reconcile Supabase-login identity vs Play-account identity for entitlement? (Two people could share one Play account; one person could have two logins.)

---

## 4. Candidate free-vs-paid feature map (BRAINSTORM — nothing locked)

From Alvin's throwaway examples + the natural gate points. **Explicitly not decided** — this is raw material to sort in grilling. Each row needs: which gate type (§1.2), which lapse policy (§2.1), individual-vs-shared (§2.2).

> **Superseded** by §8 (concrete map) and §10.1 (the locked `Feature`/`PlanLimits` code contract). Kept for historical brainstorm context only.

| Candidate feature | Likely gate type | Notes / tension |
|---|---|---|
| Extra theme palettes (beyond a free subset) | Soft gate + revert-on-lapse | How many free? Which ones? Reverting active theme on lapse (§2.1). |
| Custom fonts (Horizon #8) | Soft gate | Unbuilt — born gated if built after this. |
| In-app calculator | Soft gate | Small feature; is it even worth gating, or a free delighter? |
| Shared account count cap | Plan limit (hard) | Individual or couple-governed? (§2.2) |
| Shared category count cap | Plan limit (hard) | Same. |
| Savings-goal count cap | Plan limit (hard) | Lapse policy critical (§2.1) — financial data. |
| Budget-period stepper granularity | Hard gate on options | Which periods free (e.g. monthly only) vs paid (1d/1w/12m)? |
| "Paid for partner" debt feature | Hard gate | Couples-only already; gating a couples feature → §2.2. |
| Recurring rules (count or at all) | Plan limit? | Not in Alvin's list but a natural cap point. |
| Widget(s) | Soft gate? | Not listed; flag as a question. |
| AI companion (Horizon #3) | Its own model (BYOK + capped free tier) | Already has a separate monetization design; how does it interact with the subscription? Is AI *included* in the sub, or add-on? |

> **Q4.1** What's the **free tier's shape** — "generous free, pay for power/cosmetics" or "thin free, pay for the real app"? This single choice sets the tone for every row above.
> **Q4.2** Which features are *never* gated on principle? (Core expense tracking? Basic budgeting? Pairing itself?) A finance app that gates recording your own spending feels predatory — where's our floor?
> **Q4.3** How does the AI companion's existing hybrid model (Horizon #3) fold in — included, add-on, or separate SKU?

---

## 5. Architecture placement (once decided)

> **Superseded by §10** (concrete build spec) + §9. The sketch below is the first cut — e.g. `SubscriptionStatus` became `is_premium`/`premium_until` on the `users` row (D2), and RTDN is deferred (D4). Kept for the module-boundary reasoning (Q5.1/Q5.2).

- `core/billing/` — Play Billing Library wrapper, `queryPurchasesAsync`, purchase flow, RTDN/verification if server-gated.
- `core/entitlement/` (or fold into billing) — `EntitlementRepository`, `SubscriptionStatus` cache (DataStore/Room), refresh scheduling.
- `core/entitlement/FeatureAccess` + `PlanLimits` — the single choke point all gates consult.
- `feature/subscription/` — paywall screen, manage-subscription UI, upsell surfaces.
- Gate placement: **UseCases** for hard gates/plan limits (they own data access); **ViewModels** for soft gates (routing to paywall).
- Tests (tier-1, per Testing Policy): entitlement state machine (active/grace/lapsed/offline-stale), plan-limit boundary math, lapse-policy transitions, couples-governance resolution. All JVM-only, no billing SDK on the JVM path (wrap the SDK behind an interface so it's fakeable).

> **Q5.1** One `core/billing` module or split billing/entitlement? (Entitlement is testable pure logic; billing is the un-unit-testable SDK edge — splitting keeps the tier-1 suite clean.)
> **Q5.2** Confirm the SDK gets wrapped behind an interface so entitlement logic stays JVM-testable.

---

## 6. Sequencing (proposed, to confirm)

1. **Grill this doc to resolution** → write ADR(s): entitlement/caching, lapse policy, couples-governance, gate architecture.
2. **Build the dormant infrastructure** (`core/billing`, `core/entitlement`, `FeatureAccess`/`PlanLimits`, paywall screen) — kill-switch OFF, everything unlocked. Tier-1 tests for the entitlement state machine.
3. **Build remaining paid-candidate Horizon features *through* the gate** (custom fonts, extra palettes) so they're born gated.
4. **Wire real gates** into existing features per the decided feature map.
5. **Play Console setup** (products, pricing, test tracks) — staging first.
6. **Flip enforcement on** only when beta is over and the paid set justifies the price (Alvin's explicit go, per dev-phase rule).

> **Q6.1** Confirm infrastructure-first-enforcement-later, and that it slots *ahead of* most remaining Horizon items rather than after all of them.

---

## 7. Ad banner placement (separate monetization lever)

> **Direction set:** ad-supported free, ad-free premium (`NO_ADS` in §10.1; AdMob concretes in §10.9). The remaining soft-open piece is Q7.1 — whether we ship ads *at all* — but the layout below is the plan if we do.

Distinct from the paywall above — this is whether free-tier users see a banner ad at all, not what Premium unlocks. Raised in conversation 2026-07-07.

**Sketch placement:** anchor a banner ad directly above the bottom nav (Records | Analysis | Budgets | Accounts | Categories), and float the sticky Add Transaction FAB just above the banner instead of its normal resting spot.

- Free-tier only — collapses to **zero height**, not just invisible, for subscribers, so the FAB re-settles to its normal position on entitlement change.
- Reserve the space as part of the Scaffold's bottom bar layout, not an overlay — ad load is async and can pop in late; an overlay would jump/cover list content when it resolves.
- FAB offset should be computed from the **measured** banner height (standard adaptive banner sizes reflow with device width), not a hardcoded constant.
- Likely library: Google AdMob (adaptive banner).

> **Q7.1** Do we want ads at all, or does the subscription's free tier stay ad-free and ads are dropped entirely? This needs its own grill pass — interacts with Q4.1 (free tier's shape/tone) and could undercut the "generous free" framing if free = ad-supported *and* feature-capped.
> **Q7.2** If adopted, does the banner show on every bottom-nav tab, or only some (e.g. Records, the highest-traffic screen)?

---

## 8. Concrete free/premium split — Alvin's first pass (2026-07-07)

Alvin's first concrete tier map. This supersedes §4's brainstorm rows where they overlap, but is **still pre-grill** — the caps and lines below are a proposal to stress-test, not ADR-locked numbers. Two structural things this pass settles: (a) it implies a **single paid tier** (free vs. premium — resolves Q1.6 toward a boolean, not free/plus/pro), and (b) it implies **ads exist on free and premium removes them** (gives §7 a direction: ad-supported free, ad-free premium).

Throughout, "**(max count is implicit)**" is Alvin's phrasing for *a ceiling high enough to feel unlimited but still bounded so sync/storage stays safe* — i.e. premium is not truly infinite, just effectively so. Good instinct; keep it, and apply the same idea to the two "how do I even limit this?" cases (notes count, below).

### 8.1 The map

> **Locked numbers live in §10.1.** This table is Alvin's first pass; the code-contract caps — including the resolved "(implicit max)" blanks and the "paid-for-partner" row — are in §10.1.

| Area | Free | Premium | Gate type (§1.2) | Notes / tension to grill |
|---|---|---|---|---|
| **Ads** | Banner ads shown | No ads | Soft (entitlement toggles ad visibility) | Ties to §7. Free = ad-supported. Confirm we want ads at all (Q7.1). |
| **Analysis — range stepper** | (current stepper) | **3m / 6m / All** quick-range | Hard gate on the longer ranges | *New capability, not just a gate* — see §8.3. Which ranges are free vs. paid? |
| **Records — recurring calendar** | Locked (blurred preview?) | Full recurring calendar | Soft gate w/ blurred upsell | "Maybe just blur it?" — presentation Q, see §8.2. |
| **Records — month stepper depth** | back to **−12 months** | back to **−12 − n** (deep history) | Hard gate on depth | **DECIDED (2026-07-07):** no future beyond the current month; free = last 12mo; premium extends the past. Was uncapped both ways — net-new logic. See §8.2. |
| **Add txn — paid for partner** | ? | ? | Hard gate (couples surface → §2.2) | Is the *feature* premium, or just its cap (couple debt entries, below)? Governance Q. |
| **Add txn — receipts** | **1** photo | **2–3** photos | Plan limit (hard) | Existing `TransactionImage.MAX = 3`. Free cap = 1. Lapse Q on already-attached 2nd/3rd photo (§2.1). |
| **Accounts (personal)** | **10** | **100** *(implicit max)* | Plan limit (hard) | — |
| **Accounts (shared)** | **1** | **50** *(implicit max)* | Plan limit (hard) | Shared surface → whose entitlement unlocks? (§2.2) |
| **Categories (personal)** | **10** | **150** *(implicit max)* | Plan limit (hard) | — |
| **Categories (shared)** | **1** | **30** *(implicit max)* | Plan limit (hard) | Shared surface → §2.2 |
| **Budgets** | **5** | (implicit max) | Plan limit (hard) | — |
| **Budgets — rollover + reset rollover** | ✗ | ✓ | Soft/hard gate on the toggle | ADR-0036/0041 already built the rollover machinery — gating is just hiding the toggle. |
| **Calculator module** | ✗ (entire module) | ✓ | Soft gate | Module exists (`feature/calculator`). §4 flagged: is a calculator a strong enough paid lever, or a free delighter? |
| **Savings goals** | **5** | (implicit max) | Plan limit (hard) | Financial data → lapse policy critical (§2.1). |
| **Savings goals (shared)** | **1** | (implicit max) | Plan limit (hard) | Shared surface → §2.2 |
| **Notes — attachments** | **0** (none) | **3** photos | Hard gate | Harshest line in the map — clearest "real cost" (storage) lever, but collides hard with existing beta notes that have images (§2.1). |
| **Notes — character limit** | **5,000** | **~50,000** | Hard gate — tiered limit | **DECIDED (2026-07-07):** tiered (Alvin chose over my single-ceiling rec). Caveat: 5k is tight for rich text — one pasted list exceeds it; adjustable. See §8.2. |
| **Notes — total count** | ~10,000 (implicit) | ~10,000 (implicit) | *Not a paywall lever* | **DECIDED (2026-07-07):** same unadvertised ceiling both tiers; guards sync/storage, no UI/upsell. See §8.2. |
| **Themes — palettes** | **Rose, Peach** (2) | **+ Mauve, Sage, Mocha, Lavender** (6 total) | Soft gate + revert-on-lapse | Lapsed user on a paid palette → revert to Rose/Peach (§2.1, Q2.1). |
| **Couple — active debt entries** | **10** | **100** | Plan limit (hard) | Shared surface → §2.2. "Active" = un-settled; settled ones shouldn't count. Confirm. |

### 8.2 Open questions Alvin raised (answered inline, still to confirm)

> **Q8.1 — Records month stepper: cap direction.** ±12 from current, *or* disable future entirely and cap the free window at [−12 … present]?
> **RESOLVED (2026-07-07): disable future beyond the current month; free window = [−12 … current], premium extends the past to [−12 − n … current].** Rationale: a ledger has no data in future months, so forward stepping shows empty screens and confuses. *Caveat:* recurring rules **do** project forward (scheduled upcoming), so "the future is empty" isn't strictly true — if the recurring calendar is where you view upcoming, keep future navigation *there* and cap only the main Records ledger. Also note gating *history depth* is an unusual lever (most apps don't charge for scrolling back) — defensible as "deep history = premium," but keep the free −12 window a **full year** so year-over-year budgeting feels available on free.

> **Q8.2 — Recurring calendar: "just blur it?"** Yes — blurred/locked preview is the right upsell shape (soft gate, §1.2): the feature is visible and desirable, tapping routes to paywall. Confirm whether the *calendar view* is the premium thing or recurring rules themselves (creating/running recurring rules should almost certainly stay free — they're core money mechanics; only the *calendar visualization* is the delighter to gate).

> **Q8.3 — Notes character limit: is 5000 good, for both tiers?** **RESOLVED (2026-07-07): tiered — free 5,000 / premium ~50,000.** Alvin chose to make the limit a paywall lever (over my single-ceiling recommendation below). Caveat kept on the record: 5,000 is tight for a rich-text note — a pasted list can exceed it, so free may feel constrained; the number is adjustable if it bites in testing. My original analysis, for context: A character limit should exist as a **storage/sync/render safety valve for both tiers**, *not* as a paywall lever (free 5000 / premium more reads as petty and is hard to communicate). 5000 is probably **too low** for a rich-text notes app — a single long checklist or pasted list blows past it. Suggest a generous single ceiling (order of **20k–50k characters**, ≈ 4–10 pages) applied to everyone. It protects the editor/sync, not the wallet. Confirm the number.

> **Q8.4 — Notes total count: limit it? 10000/user?** **RESOLVED (2026-07-07): yes — an unadvertised ~10,000/user ceiling, same for both tiers.** Don't expose this as a user-facing cap or a paywall lever — 10k notes is effectively unlimited for a human. Treat it exactly like your "**(max count is implicit)**" pattern: an internal ceiling that guards sync/storage, unadvertised, same for both tiers. Pick a number you're comfortable syncing (10k is fine). No UI, no upsell.

> **Q8.5 — Savings goals: is 5 / 1-shared the *free* cap, and what's premium?** The map reads 5 personal / 1 shared as free caps; premium = implicit max. Confirm that's the intent (vs. 5 being the hard ceiling for everyone). And because goals are financial data, the **lapse policy (§2.1) matters most here** — a subscriber with 3 goals who lapses to a free cap of 5 is fine, but if the free cap were *below* their count, we hit the freeze-vs-hide problem. With free=5 that's unlikely to bite in practice; flag only if the free cap ever drops.

### 8.3 Tensions I'm flagging (these need a decision before build)

- **T1 — RESOLVED (2026-07-07): FREEZE. The free caps are *below* current beta/test data (the migration edge of §2.1/Q2.2).** Notes free = **0 attachments**, but existing beta notes (yours and Patty's) already have images (`MAX_ATTACHMENTS = 3`). Receipts free = **1**, but transactions already have up to 3. When enforcement flips on, what happens to *already-attached* photos on a free account? The dev-phase rule says don't design backfills for real users — but **our own accounts will trip this the moment the switch flips.** **Decision: freeze** — existing over-cap items (attachments, receipts, and every count-capped entity: goals, shared accounts/categories) stay visible and read-only; only *new* creation past a free cap is blocked; nothing is ever deleted. This also settles **Q2.1** for data-bearing count-capped entities and fixes the gate's shape as *block-on-create*, not block-on-exceed.

- **T2 — RESOLVED (2026-07-07) by D1 + D2.** "Paid for partner," shared accounts/categories/savings goals, shared budget, and the couple debt-entry cap are all **shared surfaces**. The governance question ("if only one partner buys, whose entitlement sets the cap?") is answered: **either-partner-unlocks-both (D1)**, with entitlement made visible across the couple via `is_premium`/`premium_until` on the synced `users` row (D2, closes Q2.4) — the doc's former hardest problem, now closed. Every shared-cap row in §8.1 resolves through D1's `me.active || partner.active` rule (§10.1).

- **T3 — Some gates are weak levers.** Gating the **calculator** entirely and gating **history-scroll depth** are low-value/mildly-annoying levers (harmless as soft upsells, but they won't move subscriptions and a finance app gating a calculator can read as petty). Fine to keep as garnish; just don't count on them. The load-bearing levers here are **attachments/receipts (real storage cost), extra palettes (cosmetic want), no-ads, and the shared-surface caps** — make sure those feel worth ₱/month on their own.

### 8.4 Non-paywall changes surfaced in this draft (route OUT of this doc)

Alvin flagged that some items here aren't really paywall. Pulling them out so they don't get lost:

1. **Add an explanatory caption under the "Paid for partner" toggle in Add Transaction.** **Scope RESOLVED (2026-07-07): a static helper caption, NOT a description input field** — it mirrors the "Private" toggle's caption ([AddTransactionScreen.kt:313-325](../../app/src/main/java/com/iponlove/app/feature/transactions/presentation/AddTransactionScreen.kt#L313-L325)); the "Paid for {partner}" toggle currently has none ([AddTransactionScreen.kt:328-349](../../app/src/main/java/com/iponlove/app/feature/transactions/presentation/AddTransactionScreen.kt#L328-L349)). Pure UI, no data field, Sonnet-level. **Booked as a concrete change in [v1.6.5.md](v1.6.5.md) Item 1** (non-paywall).
2. **Analysis 3m / 6m / All quick-range.** Listed as a gate, but the *range selector itself may not exist yet* — the current analysis stepper is DAY/WEEK/MONTH/QUARTER/SEMI/ANNUAL/ALL_TIME with infinite stepping ([AnalysisViewModel.kt:203-209](../../app/src/main/java/com/iponlove/app/feature/analysis/presentation/AnalysisViewModel.kt#L203-L209)). So "3m/6m/all" is partly a **build** (new rolling-window ranges), then a gate. Separate the build slice from the gate decision. **Booked as a build change in [v1.6.5.md](v1.6.5.md) Item 3** (the gate stays here).

---

## 9. Resolved architecture — grill #2 (2026-07-07)

Six load-bearing decisions from the second grill pass. These close the biggest open questions and fix the shape of the build. Each cascades — read the consequences, not just the verdict.

**D1 — Couples governance = either-partner-unlocks-both (closes T2 / Q2.3).** One partner's active subscription unlocks *every shared surface* for **both** partners: shared accounts/categories, shared budget, shared savings goals, partner debt tracker + its entry cap, combined view, "paid for partner." Individual surfaces (own theme palette, own calculator, own personal caps, no-ads, own receipts) stay per-user — each partner unlocks their own. A couple needs exactly **one** subscription for the joint stuff. Best PH-market adoption ("get your partner to pay").

**D2 — Entitlement propagation = field on the synced `users` row (closes Q2.4, and Q1.1).** Add `is_premium` + `premium_until` (plus `entitlement_source`, `entitlement_checked_at`) to the `users` row — already a synced entity (ADR-0013), already replicated to the partner through the redacting view (ADR-0005). Consequences:
- Entitlement is **not** device-local DataStore — it's a **synced Room column**. Forced by D1 (Patty's device must see Alvin's status). Q1.1's "per-device simplicity" is dead; entitlement joins the sync fabric.
- Effective access: shared surface = `me.active || partner.active`; individual surface = `me.active`.
- The redacting partner view must **expose** `is_premium`/`premium_until` (not private/redacted — they're needed for shared-unlock). Add to the view projection.
- Realtime (ADR-0015) is an **accelerator only**: a purchase broadcasts so the partner unlocks within seconds; the synced column is the durable truth if the broadcast is missed.
- FK/sync order unchanged — `users` is already first (ADR-0009), so entitlement lands before anything that gates on it.

**D3 — Config = hardcoded defaults + remote override (closes Q0.2).** `PlanLimits` and the enforcement kill-switch ship as compiled-in constants (safe defaults: **enforcement OFF**, caps effectively unlimited, so nothing locks until we say so). A single Supabase config row (`app_config`/`feature_flags`, cached in Room, read locally, refreshed on the existing sync trigger) can override: (a) the master `enforcement_enabled` flag, (b) any individual cap, (c) per-user premium grants (beta comp — §10.8). Flipping enforcement or tuning a cap needs **no release** — satisfies §8.3's "adjustable caps" caveat.

**D4 — Server verification deferred; all gates client-side for now (closes Q1.3, narrows Q1.4).** No RTDN, no Supabase-side purchase verification in this build. Trust model is explicitly *"client-side `queryPurchasesAsync`, spoofable on rooted devices, acceptable"* — every gated item is cosmetic, a count cap, or ad-removal, none with real server cost. **Stub kept:** the AI companion (Horizon #3) is the first server-cost feature and *will* require RTDN → Supabase verification before it can gate on entitlement. Documented future addition, out of scope here.

**D5 — Free tier = generous (closes Q4.1 / Q4.2).** Load-bearing principle for all copy and every cap: **recording your own money is never gated.** Always free, both tiers — full personal expense/income tracking, core budgeting, account/category management within free caps, couple **pairing + combined view** (the headline feature ships free), budget-alert notifications, home-screen widgets. Premium sells *more* (higher caps), *cosmetics* (palettes, later fonts), *no-ads*, and *delighters* (calculator, recurring calendar, extended analysis ranges, deep history). The wall is around power and polish, never around the core ledger.

**D6 — Widgets + budget-alert notifications = free (fills the §4 gap).** Both stay free on both tiers (follows D5) — habit/retention surfaces that feed conversion elsewhere; gating them reads as petty. Removed from the paywall lever set.

**D7 — Billing model = one-time purchase, ₱249 (2026-07-07; resolves the subscription-vs-one-time pivot and Q3.2/Q3.3).** Premium is a **single non-consumable in-app purchase** (`INAPP`), **₱249** — not a recurring subscription. Consequences:
- **Entitlement layer stays billing-model-agnostic anyway:** `is_premium: Boolean` + **nullable** `premium_until: Instant?` (+ `entitlement_source` from D2). One-time = `premium_until = null` (never expires); gate check = `is_premium && (premium_until == null || premium_until > now)`. This made the pivot near-zero-cost and keeps a future re-pivot to subscription cheap — **D1–D6 are unchanged** (they read a boolean, not a billing model).
- **State machine collapses** (§10.4) to `OWNED / NOT_OWNED`: no grace, no billing-retry, no offline-stale re-lock. Best-case offline story — once acknowledged, `queryPurchasesAsync(INAPP)` returns the entitlement permanently.
- **Lapse (§2.1 / T1) only fires on refund.** A paid user never drops below premium, so "was-premium → frozen over-cap data" can't occur except via a Play refund. Freeze reduces to "free users hit block-on-create." Theme revert-on-lapse (§10.1) likewise only on refund.
- **`core/billing` is the only module that differs** from the subscription sketch: query `INAPP` not `SUBS`, acknowledge-once (no consume — it's a durable entitlement), "Manage subscription" → **Restore purchase**. Simpler Play compliance (no recurring-billing disclosures).
- **No free trial** — the generous free tier (D5) *is* the trial.
- **AI (Horizon #3) stays OUT of one-time Premium** — ongoing per-call cost can't be funded once-off. Its monetization is resolved separately in **D8** below. D4's "AI is a separate model" note now has a concrete financial reason: one-time revenue + recurring AI cost is upside-down.
- **Theme revert-on-lapse** now fires on active-palette reconciliation, not only refund — see G8 / §10.1.

**D8 — AI companion monetization = separate credits add-on, server-metered (2026-07-07; closes Q4.3).** AI (Horizon #3) is **not** part of the ₱249 Premium — it's a distinct add-on so "Premium" never implies "unlimited AI." Shape:
- **Included starter allowance** of AI credits bundled with Premium (goodwill; everyone gets to try it).
- **Consumable top-up credit packs** (Play *consumable* product, e.g. ~₱99 / 100 credits *(placeholder)*) for continued use — the secondary revenue stream.
- **BYOK** (bring-your-own-key) in Settings as the power-user escape hatch — zero marginal cost to us, removes the heavy-user tail entirely.
- **Guardrails:** pre-aggregate on-device and send the model a *compact summary*, never the raw ledger (cuts token cost ~10× and keeps financial data on the device); per-feature cooldown; routine parsing (SMS/receipts) stays offline, cloud only for explicit high-level coaching.
- **Architectural consequence — the real cost of AI, not the tokens:** paid credits **break D4's client-side trust model** (a rooted client could mint infinite credits), so AI requires the server-side piece D4 stubbed: a **Supabase Edge Function proxy** that checks + decrements a **server-authoritative credit balance** before calling the provider, plus **purchase-token verification** (Play RTDN / server verify) for the consumable packs. `core/billing` then handles **both** product types — Premium = non-consumable, credit packs = consumable (`consumeAsync`).
- **Model/provider** (Gemini Flash / Claude Haiku / GPT-4o-mini) is cheap enough across the board that it doesn't change this design — picked at build time, not now.
- **Status: deferred Horizon #3**, built behind the dormant client-side paywall infra. This is the app's *first* server-cost feature and the reason D4's RTDN/verification stub exists.

---

## 10. Concrete build spec — "lay it all down"

What the build actually consumes. Numbers marked *(proposed)* are stress-test candidates, not locked; every cap is remote-overridable per D3, so shipping low-risk first values is safe.

### 10.1 `Feature` enum + `PlanLimits` (the code contract)

Two kinds of gate, two data shapes (per §1.2, §1.5).

**Boolean features** — `enum class Feature`, each unlocked on premium only:

| Feature key | Gate type | Surface | Scope |
|---|---|---|---|
| `NO_ADS` | soft (toggles ad visibility) | ad banner (§7) | individual |
| `RECURRING_CALENDAR` | soft (blurred preview) | Records | individual |
| `BUDGET_ROLLOVER` | soft (hide toggle) | Budgets | individual |
| `CALCULATOR` | soft (module lock) | Calculator | individual |
| `ANALYSIS_EXTENDED_RANGES` | hard (3m/6m/All) | Analysis | individual |
| `DEEP_HISTORY` | hard (past beyond −12mo) | Records stepper | individual |

**Themes** are an **allowlist**, not a boolean/count: free set = `{Rose, Peach}`; premium = all six. The one **non-freeze** lapse rule (a cosmetic can't be frozen "read-only") is **active-palette reconciliation** (G8, 2026-07-08 — supersedes "only on refund"): on *any* entitlement/enforcement change, re-check the active palette against `Effective access` and, if now locked, swap to a free default — **non-destructively** (the chosen palette is remembered and auto-restores on re-unlock). Its main trigger is **enforcement flip-day** (every free user on a premium palette at once), not just refund.

**`PlanLimits` (count caps)** — one instance per tier; shared caps resolved with `me.active || partner.active` (D1):

| Field | Free | Premium *(proposed)* | Scope |
|---|---|---|---|
| `maxPersonalAccounts` | 10 | 100 | individual |
| `maxSharedAccounts` | 1 | 50 | **shared (D1)** |
| `maxPersonalCategories` | 10 | 150 | individual |
| `maxSharedCategories` | 1 | 30 | **shared** |
| `maxBudgets` | 5 | 100 | individual |
| `maxPersonalSavingsGoals` | 5 | 50 | individual |
| `maxSharedSavingsGoals` | 1 | 20 | **shared** |
| `maxCoupleDebtEntries` (active/un-settled) | 10 | 100 | **shared** |
| `maxReceiptPhotos` | 1 | 3 | individual |
| `maxNoteAttachments` | 0 | 3 | individual |
| `maxNoteChars` | 5,000 | 50,000 | individual |
| `maxNotes` (both tiers) | 10,000 | 10,000 | individual, unadvertised |

Resolves the §8.1 "(implicit max)" blanks (budgets 100, savings 50/20) and the "paid-for-partner ?/?" row: **the feature is free** (core couple money mechanics, D5); **only its cap — `maxCoupleDebtEntries` — is gated.**

### 10.2 Built-now vs born-gated (sequencing input for §6 steps 3–4)

Each §8.1 row tagged so the build knows whether to *wire a gate* or *build-then-gate*:
- **Built today — wire gate only:** themes/palettes, receipts (`TransactionImage.MAX`), budgets, budget rollover (ADR-0036/0041), calculator module, personal/shared accounts & categories, notes attachments + char-limit, couple debt entries, **savings goals** (personal + shared + contributions), ads (new but pure infra).
- **Build-then-gate (net-new capability first):** analysis 3m/6m/All ranges (§8.4 item 2 — build slice booked in v1.6.5), recurring calendar view, deep-history stepper logic (§8.2 — new past-window logic).
- **Born-gated (future Horizon, gated at birth per §6):** custom fonts (#8), AI companion (#3 — also server-gated, D4 stub).
- **Verified built (2026-07-07):** savings goals shipped with personal + shared/partner variants and contributions (`feature/savings` — `PartnerSavingsGoalDto`, `GoalContributionTableSyncer`, `SetGoalArchivedUseCase`) → wire-only, and the shared-goals cap (§10.1) is real.

### 10.3 Paywall & upsell UX (the missing screen work)

- **Central paywall screen** (`feature/subscription`): headline tied to D5 ("Unlock more of Love, Ipon"), benefit list generated from the tier map, single **₱249 one-time** price + primary CTA ("Get Premium"), **Restore purchases** link, Terms + Privacy links (Play-required, §10.6). No monthly/annual selector and no trial (D7). When already owned it shows a **"Premium — active"** state + Restore (no renewal/cancel — one-time).
- **Upsell entry points — every gate needs a defined trigger:**
  - *Hard count-cap hit:* on create-attempt past the free cap → bottom sheet "You've reached the free limit of {N} {things}. Go Premium for up to {max}." + Upgrade CTA. **Block-on-create, never block-on-exceed** (T1 freeze).
  - *Soft gate:* lock badge on the feature; tap routes to paywall. Recurring calendar = **blurred preview** behind a lock (§8.2); palettes = greyed swatches with a lock; calculator = locked module tile; extended analysis ranges & deep-history = greyed/disabled options with an "Unlock" affordance.
- **Locked-state presentation is per-gate** — specify each (blur vs grey vs disabled vs bottom-sheet) so it's not improvised at build time.
- **Post-purchase:** success confirmation → entitlement cache refresh → surfaces unlock **without app restart** (observe the entitlement `StateFlow`). Partner's shared surfaces unlock on the next sync/realtime tick (D2).
- **Settings:** a **"Premium"** row (upgrade CTA when free, "active" when owned) + **Restore purchases** (also covers the logged-out / new-device Play-account edge).

### 10.4 Entitlement state machine (offline-first contract)

> **Per D7 (one-time), this collapses to `OWNED / NOT_OWNED`** — the grace/lapse/offline-stale states below are dormant, retained via the same nullable-expiry code path only in case of a future re-pivot to subscription.

States: `UNKNOWN` (cold start, no cache) · `ACTIVE` · `IN_GRACE` (Play billing-retry) · `ON_HOLD/LAPSED` · `EXPIRED/NEVER`.
- **Cold-start = fail-open (G4 / ADR-0044, 2026-07-08 — supersedes the earlier "free/locked" default).** A device that does not yet know `Enforcement` is ON (fresh offline install, no sync) treats it as OFF and stays fully unlocked, self-healing on first foreground sync. Since `is_premium` (users row) and `enforcement_enabled` (app_config) both live in Room and arrive on the same sync (users is first in the FK order), there is no partial state where enforcement is known-ON but entitlement unknown — so a paying customer reinstalling offline is **never** wrongly locked. Refresh eagerly on first foreground.
- **Grace:** lean on Play's billing-retry grace (up to ~30 days) — treat `IN_GRACE` as ACTIVE; don't punish a failed-renewal user mid-retry.
- **Offline-stale tolerance (closes Q1.2):** keep **last-known-good** entitlement until a *successful* refresh contradicts it — never hard-lock a subscriber just for being offline N days. The re-lock trigger is a **successful `queryPurchasesAsync` returning "not entitled"** (foreground or WorkManager periodic revalidation), **not a timer**. No self-imposed offline expiry; Play's grace + a real network confirmation govern re-lock.

### 10.5 Pricing & products (locked 2026-07-07 — D7)

- **One-time non-consumable in-app product** `premium`, **₱249**. No subscription base plans, no free trial (the generous free tier is the trial, D5).
- In-app / store name: **"Love, Ipon Premium"** *(placeholder — confirm)*.
- Play handles PH VAT + currency; ₱249 is the buyer-facing price.
- Play Console setup (§6 step 5): create the managed product, set ₱249, wire license testers.

### 10.6 Play policy / legal compliance (Google rejects apps missing these)

- Price shown **before** purchase on the paywall (one-time — no billing period/renewal terms per D7).
- **Restore purchases** affordance (paywall + Settings) — the only "manage" path a one-time product needs.
- Privacy policy updated for billing + (if adopted) AdMob data collection; linked in-app.
- If ads adopted: "**Contains ads**" store flag + AdMob families/consent policy (§10.9).
- No cancel/manage flow (one-time, D7) — **Restore purchase** covers re-download/new-device.

### 10.7 Testing matrix + what to expect when the build lands

**Headline expectation:** the build ships **dormant (enforcement OFF, everything unlocked)**, so — *visually, almost nothing changes.* No caps bite, no locks appear, no paywall blocks anything. If a normal walkthrough looks different with enforcement OFF, that's a bug. The deliverable is machinery, not a user-facing change. Testing is therefore two modes.

**Mode A — dormant (the default that ships): confirm invisibility.** Create >10 accounts, >5 budgets, use all 6 palettes, open the calculator, attach 3 note photos → **all still work**. Gates exist but are bypassed because `enforcement_enabled = OFF`. The Premium Settings entry (Item 12) is inert/hidden until the explicit go.

**Mode B — enforcement-ON rehearsal:** flip `enforcement_enabled` ON in **staging** `app_config` to dress-rehearse the eventual flip day. This is the real test lane — the exact experience real users get on flip day, and the one thing the dormant default hides. Walk it with the test accounts (`testdev2-5` + Alvin + Patty). Force each state with **no real charges**:

> Under one-time billing (D7) only the Owned / Not-owned / Offline-stale / Over-cap-frozen / Couple-cross-unlock / Cold-start / Palette-revert rows apply; Grace and Lapsed are dormant (kept for a possible subscription re-pivot).

| State | How to force | Expect |
|---|---|---|
| Dormant (ships) | Enforcement OFF (default) | Nothing locked; app behaves exactly as before |
| Free/locked | Non-comped account, enforcement ON | 11th account blocked w/ upsell sheet; premium palette + calculator show locks |
| Over-cap frozen (T1) | Seed a note w/ 3 images + a txn w/ 3 receipts, *then* flip ON | Existing items stay **visible + read-only** (never deleted); only *new* creation blocked |
| Beta comp not-wiped (G7) | Grant `testdev2` via remote override, then background/foreground repeatedly | Stays premium — reconcile loop does **not** wipe the `GRANT` on foreground |
| Couple cross-unlock (D1/D2) | One account premium (grant), partner free | Partner's **shared** surfaces unlock after sync; partner's **own** palettes/calculator stay locked |
| Fail-open cold-start (G4) | Fresh install + airplane mode, enforcement ON in config | Everything unlocked until first sync, then settles |
| Palette revert flip-day (G8) | Free account on a premium palette, flip enforcement ON | Active palette reverts to a free default; re-granting premium **restores the chosen palette** |
| Offline-stale | Airplane mode after caching premium | Last-known-good holds; no re-lock on a timer |
| Real purchase | Play license-tester buys the ₱249 product on a test track | Entitlement flips, surfaces unlock **without restart**; Restore works after reinstall |

**Two most-likely first bugs (watch these):**
1. **`GRANT` flip-flop (G7)** — if comps toggle each app foreground, the reconcile loop isn't respecting `entitlement_source`. Easiest thing to get wrong.
2. **Freeze vs. delete (T1)** — over-cap data must go *read-only*, never disappear. A finance app that hides a user's data on flip day is the worst-case failure; test it explicitly before any flip.

### 10.8 Edge states

- **Beta-tester comp:** grant Alvin + Patty + `testdev2-5` premium via the D3 **remote per-user override** (not a real Play purchase) — doubles as the primary "unlocked path" test lane and prevents the T1 freeze from tripping our own accounts on flip day.
- **Refund / revocation:** the *only* way a one-time buyer loses premium (D7). With no RTDN (D4), it's caught on the next `queryPurchasesAsync` — accept a poll-lag (up to the WorkManager interval) before re-lock. Fine for cosmetic gates.
- **Plan change:** N/A — one-time has no cadence to switch (this was a subscription-only concern).
- **Logout / Play≠Supabase identity (Q3.4):** entitlement (Play-account-bound) persists across Supabase logins on the same device; the couple-propagation column is Supabase-login-bound, so it correctly follows whoever is logged in. Restore purchases covers a new device on the same Play account. Two logins sharing one Play account both read premium on that device — acceptable.

### 10.9 AdMob concretes (§7 had placement, not SDK reality)

- App ID + banner ad-unit IDs per env (staging/prod); **test ad units** in stagingDebug so testers never hit live inventory.
- UMP/consent gathering (Google requires a consent flow even outside GDPR), test-device registration, "Contains ads" store flag.
- SDK init in `Application`; adaptive banner in the Scaffold bottom bar (not overlay, per §7); **zero-height collapse** driven by the `NO_ADS` entitlement so the FAB reflows.

### 10.10 Analytics (minimal)

`paywall_impression(source)`, `upsell_tap(feature)`, `purchase_started`, `purchase_success`, `purchase_cancelled`, `restore`, `refund_detected`. Enough to know which levers convert; no more.

---

## 11. Grill #5 resolutions (2026-07-08) — the mechanism seams under D1–D8

Fifth pass drilled the seams *beneath* the decisions: how the entitlement column is actually written and reconciled, what entitlement may never touch, the cold-start fail direction, cap-counting semantics, per-instance scope, beta-comp coexistence, and the last open product lever (ads). All ten are captured as glossary terms in `CONTEXT.md` (`Entitlement`, `Enforcement`, `Freeze`, `Effective access`, `Cap count`, `Premium grant`) and the load-bearing four are formalized in **ADR-0044 — entitlement is a client-trusted advisory column on the synced users row**.

- **G1 — `Cap count` = all non-deleted rows, archived included.** Archive exists on accounts, categories, and savings goals; an archived row still "reflects" (its balance + transactions stay in Analysis, the record persists), so it still counts. Only a soft-delete frees a slot ⇒ ungameable (there is no un-delete affordance). **Sole exception:** partner debts count un-settled entries only (a settled debt is a completed obligation, not held against the cap). Resolves the `maxCoupleDebtEntries "(active/un-settled)"` note and removes any need for archive-gating or a separate archived cap.

- **G2 — Entitlement write/reconcile (→ ADR-0044).** The `users` entitlement columns are a **client-maintained cache of that user's Play state**, reconciled on every foreground `queryPurchasesAsync`. Trust is asymmetric: Play-authoritative for self, column-authoritative (trusted unconditionally) for the partner.

- **G3 — Entitlement is a pure client-side advisory layer (→ ADR-0044).** It never gates sync, replication, or visibility; caps are best-effort at create-time only (concurrent cross-device creates can transiently exceed a cap — tolerated by `Freeze`). Closes §2.2's "does gating change what replicates?" → **no, never.** Unpair-over-cap resolves as ordinary `Freeze`.

- **G4 — Cold-start = fail-open (→ ADR-0044).** "Unknown `Enforcement` ⇒ unlocked." **Supersedes §10.4's fail-closed framing** (patched below) — a paying customer reinstalling offline is never wrongly locked, and the leak (a data-less fresh install briefly seeing cosmetics) is negligible.

- **G5 — Ads dropped.** Closes the last open item (Q7.1). No AdMob at launch: one-time ₱249 + AI credits are two revenue streams with zero per-user server burn, so nothing structurally forces ads, and every ad format costs brand/trust/logging-frequency (interstitial-on-save was rejected as a *tax on the core loop D5 protects*, plus AdMob-policy ban risk). Free users monetize **indirectly** — funnel + the couples-recruitment engine (D1 makes a free partner nag the payer) + word of mouth. **`NO_ADS` stays in the enum but dormant**, so a single hard-capped app-open ad remains a zero-rework future lever if volume ever demands it. **Supersedes the §8.1 ads row, §7, and §10.9** (AdMob work shelved, not built).

- **G6 — `Effective access` = scope follows entity ownership (per-instance).** A row on a couple-owned entity is shared (`me.active || partner.active`); a row on a user-owned entity, or a feature attached to no entity, is individual (`me.active`). So `BUDGET_ROLLOVER` on the **shared couple budget** is governed by `me.active || partner.active`, not the flat "individual" tag. §10.1's tags are common-case defaults; row ownership is the rule.

- **G7 — `entitlement_source ∈ {PLAY, GRANT, NONE}`; reconcile skips `GRANT` (→ ADR-0044).** A `Premium grant` (beta comp) is written server-side to the row so it propagates to the partner; the Play-reconcile loop skips `GRANT` rows so it never wipes a comp on the next foreground. This is what `entitlement_source` is *for*. `entitlement_checked_at` = last-reconcile diagnostic, never read by a gate.

- **G8 — Revert-on-lapse = active-palette reconciliation on any entitlement/enforcement change**, non-destructive (the chosen palette is remembered and auto-restores on re-unlock). Its **main trigger is enforcement flip-day** (every free user on a premium palette), not just refund. **Corrects D7 / §10.1's "only on refund"** (patched below).

- **G9 — AI credits are per-user (D8).** When the AI add-on is built, the starter allowance + credit balance are **per-user**, not per-couple — AI is a metered per-person cost, and the couple-unlock model (D1) is reserved for zero-cost features. Deferred with the rest of Horizon #3.

- **G10 — Analytics = a Supabase `analytics_events` table, not a third-party SDK.** For the §10.10 event set: a `core/analytics` `Analytics.log(name, source, params)` interface writes a local Room `analytics_event` row (`pending_sync`), **push-only** flush to Supabase on the existing sync trigger, funnel computed in SQL / a Supabase dashboard. No Firebase (banned), no off-device behavioral vendor (finance-app trust); covered by the existing privacy policy (only paywall-interaction events + `user_id`, no financial content). Reusable later for AI-usage events.

---

## 12. Build order (slice plan) — resume-able across conversations

The ordered, self-contained slice plan for the dormant-infra build. **We clear convos often**, so this is the single place that remembers where the build is. Tick each box as it lands.

**How to resume in a fresh convo:** run `/orient`, then say — *"continue the paywall build — next unchecked slice in `subscription-paywall-design.md` §12."* This doc + **ADR-0044** + the `CONTEXT.md` terms (`Entitlement`, `Enforcement`, `Freeze`, `Effective access`, `Cap count`, `Premium grant`) are the whole spec; nothing else needs re-reading.

**Sequencing rule — one-concern-per-commit HOLDS.** The paywall is ~a dozen vertical slices, **never one commit**. Wire-only gates are grouped **by mechanism, not by feature** (e.g. all personal count-caps = one slice), never bundling unrelated gates. Everything ships **dormant (enforcement OFF)** — nothing locks until Alvin's explicit post-beta go. Model: **Opus** for Phase 1 (S2/S4 are the cross-ADR core); **Sonnet** for the wire-only gates once the pattern is locked.

**Phase 0 — base behaviors (no infra needed; build anytime, own commits):**
- [x] Item 2 — Records future-month cap ([v1.6.5.md](v1.6.5.md) Item 2) — **DONE 2026-07-08** (scope **Records + Combined**; `MonthWindow.canStepForward`; Analysis/Budgets/Recurring left steppable by design)
- [ ] Item 3 — Analysis 3m/6m/All rolling ranges ([v1.6.5.md](v1.6.5.md) Item 3)
- [ ] Item 4 — Notes char-limit *existence* ([v1.6.5.md](v1.6.5.md) Item 4)

**Phase 1 — dormant infrastructure (enforcement OFF, nothing locks):**
- [ ] **S1 — Schema migration** (staging first): `users` entitlement columns (`is_premium`, `premium_until`, `entitlement_source`, `entitlement_checked_at`) + add them to the redacting partner-users view projection; `app_config` row (`enforcement_enabled` + cap overrides + per-user grants), cached in Room.
- [ ] **S2 — `core/entitlement` domain (Opus):** `Feature` enum + `PlanLimits` (§10.1, hardcoded defaults, enforcement OFF), `Effective access` resolver (scope-follows-ownership, G6), `Cap count` predicate (G1). JVM tier-1 tests (boundary math, scope resolution).
- [ ] **S3 — `core/billing`:** Play Billing wrapper behind an interface (INAPP query, acknowledge-once, restore), fakeable on the JVM path.
- [ ] **S4 — Entitlement reconcile loop (Opus):** cache-of-Play on foreground (G2), `GRANT`-skip (G7), fail-open cold-start (G4), read partner column via redacting view, realtime accelerator. Tier-1 state tests.
- [ ] **S5 — Paywall screen** (`feature/subscription`) + **Item 12** dormant Settings entry + Restore ([v1.6.5.md](v1.6.5.md) Item 12).
- [ ] **S6 — Analytics primitive** (`core/analytics`): Room-buffered, push-only `analytics_events` flush (G10, §10.10).

**Phase 2 — wire gates (still dormant; verify each by flipping enforcement ON in staging):**
- [ ] **S7 — Count-cap gates, grouped by mechanism:** personal accounts/categories/budgets/savings; shared accounts/categories/savings; couple debt cap. Block-on-create + `Freeze`.
- [ ] **S8 — Media caps:** receipt photos (`maxReceiptPhotos`), note attachments (`maxNoteAttachments`).
- [ ] **S9 — Boolean soft gates:** palette allowlist + revert-reconciliation (G8), calculator, budget-rollover toggle, recurring-calendar blur.
- [ ] **S10 — Build-then-gate splits:** `DEEP_HISTORY` (on Item 2), `ANALYSIS_EXTENDED_RANGES` (on Item 3), `maxNoteChars` 5k/50k split (on Item 4).

**Phase 3 — pre-flip (only on Alvin's go, post-beta):**
- [ ] **S11 — Play Console:** create the ₱249 managed product, license testers, staging test track.
- [ ] **S12 — Beta comps:** grant Alvin + Patty + `testdev2-5` premium via the remote override (§10.8).
- [ ] **S13 — Enforcement-ON rehearsal:** full §10.7 matrix in staging.
- [ ] **Flip enforcement ON** — Alvin's explicit go only.

---

## Resolution log (was: open-questions grill checklist)

**All resolved.** The original checklist — Q0.1 Q0.2 · Q1.1–Q1.6 · Q2.1–Q2.5 · Q3.1–Q3.4 · Q4.1–Q4.3 · Q5.1 Q5.2 · Q6.1 — is closed by D1–D8 and §10. The only soft-open item is Q7.1 (ship banner ads *at all*). Chronological resolutions:

**Resolved 2026-07-07 (§8):** Q8.1 (stepper — disable-future / free-12mo / premium-deep-past), Q8.3 (notes char limit — tiered 5k/50k), Q8.4 (notes count — unadvertised ~10k ceiling), Q8.2 (recurring calendar — blurred soft-gate, rules stay free), and T1 (over-cap data — **freeze**, which also settles **Q2.1** for count-capped data-bearing entities). Two concrete non-paywall changes were split out to [v1.6.5.md](v1.6.5.md) (partner-paid caption; analysis 3m/6m/All ranges).

**Resolved 2026-07-07 (grill #2 → §9–§10):** Q2.3/T2 (couples → **either-partner-unlocks-both**, D1) · Q2.4 + Q1.1 (propagation → **field on the synced `users` row** via redacting view, D2) · Q0.2 (config → **hardcoded defaults + remote override**, D3) · Q1.3/Q1.4 (**client-side only, RTDN deferred**, D4) · Q4.1/Q4.2 (**generous free** — own-money never gated, D5) · Q1.2 (**last-known-good, re-lock only on a real "not entitled" refresh**, §10.4) · widgets/notifs (**free**, D6). The concrete build spec — `Feature`/`PlanLimits` contract, paywall/upsell UX, state machine, pricing slots, Play compliance, testing matrix, edge states, AdMob — is laid down in **§10**.

**Resolved 2026-07-07 (grill #3):** billing model → **one-time ₱249 in-app purchase** (D7 — supersedes the subscription assumption throughout; entitlement stays model-agnostic via nullable `premium_until`) · Q3.2/Q3.3 → **₱249, no trial** · savings-goals feature **confirmed built** (`feature/savings`, personal + shared + contributions — wire-only, §10.2).

**Resolved 2026-07-07 (grill #4):** Q4.3 (AI monetization) → **D8** — a separate credits add-on (starter allowance + consumable top-up packs + BYOK), **server-metered** via a Supabase Edge Function (paid credits can't be tracked client-side); AI stays deferred (Horizon #3); model/provider picked at build time.

**Resolved 2026-07-08 (grill #5 → §11 + ADR-0044):** G1 (`Cap count` — archived counts, settled doesn't) · G2/G3/G4/G7 (entitlement mechanism — client-trusted advisory column, cache-of-Play, advisory-only, cold-start **fail-open**, `entitlement_source` guards comps → **ADR-0044**) · G5 (**ads dropped**, closing Q7.1 — `NO_ADS` parked dormant) · G6 (`Effective access` — scope follows entity ownership, shared-budget rollover is shared) · G8 (revert-on-lapse — active-palette reconciliation, flip-day trigger, non-destructive) · G9 (AI credits **per-user**) · G10 (analytics — Supabase `analytics_events` table, no third-party SDK).

**Still open:** nothing load-bearing. Ads are decided (**dropped**, not "ad-supported free"); `NO_ADS` remains only as a dormant future option. Deferred by design: D8 AI add-on (Horizon #3) and its per-user credit metering + server verification (the ADR-0044 prerequisite).
