# Subscription Paywall + Feature Gating — Design & Grilling Doc

**Status:** Pre-design. Nothing here is decided. This document exists to be *grilled* (`/grilling`) until every open question below is resolved, after which the settled decisions become one or more ADRs and a build plan.

**Origin:** Monetization pivot, 2026-07-06 — Love, Ipon moves from one-time purchase to recurring **Google Play subscription billing**. Recorded as Post-V1 Horizon #15 in `project-build-progress.md`.

**Model/effort guidance for building this:** Opus for the architecture (offline-first entitlement + couples-sharing gating is a cross-ADR problem); Sonnet for the mechanical gate placement once the pattern is locked.

---

## 0. The two decisions people conflate — keep them separate

1. **Infrastructure decision** — build the entitlement + gating layer. Cross-cutting, cheap now, expensive to retrofit.
2. **Enforcement decision** — actually flip the paywall on and charge.

**Working stance (to grill):** build the infrastructure *next*, before most remaining Horizon items, but ship it in **"everything unlocked / kill-switch off"** mode. Do not enforce until beta is over and the paid feature set justifies a price. Rationale: half the candidate paid features are unbuilt Horizon items (custom fonts, extra palettes, AI) — if the gating layer exists first, each is *born gate-aware* instead of retrofitted.

> **Q0.1** Do we agree infrastructure-first / enforcement-later? Or is there a reason to defer even the infrastructure (e.g. tier lines so undecided that the `Feature` enum would churn)?
> **Q0.2** What is the kill switch? A remote-config flag (Supabase row) so we can flip enforcement without a release? Or a build-flavor constant?

---

## 1. The model: entitlement + gates

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

## Open-questions index (grill checklist)

Q0.1 Q0.2 · Q1.1 Q1.2 Q1.3 Q1.4 Q1.5 Q1.6 · Q2.1 Q2.2 Q2.3 Q2.4 Q2.5 · Q3.1 Q3.2 Q3.3 Q3.4 · Q4.1 Q4.2 Q4.3 · Q5.1 Q5.2 · Q6.1

**The two that matter most and are most app-specific: Q2.1 (lapse policy) and Q2.3/Q2.4 (couples governance + how offline devices learn the partner's entitlement).** Start there.
