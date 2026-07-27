# Web Phase 0 — Design + base-app/backend prep

**Charter (set 2026-07-26):** the running ledger for the web-app track's first phase — the web-side sibling of a native `vX.Y.md` doc. Same rule: every change we decide on gets booked here as a numbered item **first** (state, request, open design tensions), so nothing slips between conversations. Web items are numbered **W#** to keep them distinct from the native global counter.

**Origin:** a 2026-07-26 "what must change in the base app before the Q4 web app?" audit (recorded in [web-build-progress.md](web-build-progress.md#origin-2026-07-26)). Every item below is **booked, not yet grilled** — this doc records the ask + the tensions; grilling produces the locked design (an ADR in the shared `docs/adr/` sequence, or a filled section of [cross-platform-contract.md](cross-platform-contract.md)).

**Two buckets:** W1/W3/W4/W5/W6 are **base-app / backend changes** (execute as native/Supabase slices, book into the in-flight native version doc when built too). W2 is the **contract spec**. W7/W8 are **web-app design** proper.

**Suggested grill order:** W1 and W2 first (highest leverage, both block real risk). W3/W4 are decisions that shape the schema, so settle before web greenfield (W8). W5/W6 are contract line-items that can fold into W2's grill. W7 folds into W1's.

---

## W1 — Lock the entitlement columns (RLS + validating write RPC)

- **Status:** BOOKED, needs grill. **Model when grilled: Opus, high** — cross-ADR (ADR-0044 entitlement + couple governance + a new `SECURITY DEFINER` write path), security-sensitive, touches the paywall's trust model. Backend/schema change (Supabase migration + possibly a client change). **Should land regardless of web timing** — web just forces the issue.
- **The problem:** `users_update` RLS is `using (id = auth.uid()) with check (id = auth.uid())` — a user can write **any** column on their own row, including `is_premium` / `premium_until` / `entitlement_source` (schema.sql:73-75). ADR-0044 knowingly accepts this as a "client-trusted advisory column": on Android you'd need to root the device or MITM TLS to exploit it, so the risk is low. **On web that assumption collapses** — anyone opens dev tools and runs `supabase.from('users').update({ is_premium: true })`, which then syncs back to their phone via our own sync engine. Couple governance (either-partner-unlocks) means one forged row unlocks both.
- **Open design tensions to grill:**
  - How to lock the three columns while keeping the row self-updatable for legit fields (`accent_color`, `avatar_motif`, display name, etc.)? Options: column-level `UPDATE` privilege revocation; a `BEFORE UPDATE` trigger that rejects entitlement-column changes unless the write comes from a `SECURITY DEFINER` context; splitting entitlement into a sibling table only an RPC can write.
  - The legit write path becomes a **receipt-validating RPC** (Play receipt for Android; whatever web uses for W7). What validates a Play purchase server-side — an edge function calling Google's API, or a trusted server component? (Supabase has **no** edge functions today — this would be the first.)
  - Interaction with ADR-0044's **offline-first / fail-open cold-start** and **cache-of-Play** design — the client still needs to *read* entitlement offline; only *writes* get locked. Confirm the cache/read path is untouched.
  - The `entitlement_source = 'GRANT'` beta-comp path (schema.sql:1209) and how it's set today — must survive the lockdown.
  - Migration/rollout: existing legit `is_premium=true` rows must not break.
- **Ties to:** W7 (web purchase path feeds the same RPC). ADR-0044, `subscription-paywall-design.md`.

---

## W2 — Freeze the cross-platform contract (determinism + sync protocol + money math)

- **Status:** BOOKED, needs grill. Skeleton exists: [cross-platform-contract.md](cross-platform-contract.md). Grilling fills + freezes each section; the doc then *is* the spec the web team builds against (like `subscription-paywall-design.md` is for the paywall). No code change on the native side (it's documenting existing behavior) — the deliverable is the frozen doc.
- **Why it's load-bearing:** the two clients converge only if the web client reproduces these *exactly*. Getting any of them wrong fails **silently** — no crash, just duplicated rows or centavo-divergent numbers that surface weeks later.
- **What must be pinned (each a section in the contract doc):**
  - **Deterministic v5-UUID schemes** — namespace `9d8f6c2e-5b1a-4f3d-9e7c-1a2b3c4d5e6f`, SHA-1/v5 (not v4, not JDK v3/MD5), RFC-4122 variant, and every `name` string verbatim: recurring `"${ruleId}:${date}"`, netting `"netting:${a}:${b}"`, paid-on-behalf `"paid-on-behalf:${txnId}"`, transfer-fee category `"builtin-category:transfer-fee:${userId}"`, starter `"starter-category:${userId}:${key}"` / `"starter-account:${userId}:${key}"`. Source: [DeterministicUuid.kt](../../app/src/main/java/com/iponlove/app/core/util/DeterministicUuid.kt) + its 6 call sites.
  - **LWW write rule** — `updated_at = max(now() + clockOffset, prev + 1ms)`, offset-corrected + monotonic (ADR-0001); clock-offset acquisition; `pending_sync` outbox flag (ADR-0002). Web must not write naive timestamps.
  - **FK push/pull order** (ADR-0009): `users → couples → accounts → categories → recurring_rules → transactions → budgets → notes → note_images` (+ partner variants, + the newer leaves: savings_goals/goal_contributions/transaction_images, and the notification inbox at the *end* per ADR-0053). Confirm the authoritative full ordering.
  - **Money math** — canonical storage `numeric(14,2)`; wire format is `double` (`BigDecimalSerializer`, `PrimitiveKind.DOUBLE`); all money aggregates are **derived per-client** (balance ADR-0007, analysis, budget %, debt splits). Pin the exact scale + rounding mode (HALF_UP, scale 2) so web (decimal.js, not native floats) matches to the centavo.
  - **Conflict resolution** — row-level LWW by `updated_at`, except shared notes (conflict copy, ADR-0003); partner-row purge-on-unshare/delete (ADR-0005); `EnsureCurrentUserRow` before any write (ADR-0013); couple ops are RPC-only (ADR-0006/0008).
- **Ties to:** absorbs W5 (seeding) and W6 (images) as line-items; every native ADR listed above.

---

## W3 — Recurring materialization + budget-alert evaluation: server-side vs. web-reimplementation

- **Status:** BOOKED, needs grill/decision. Model TBD (server-side path = new Supabase edge function + pg_cron, first of their kind here).
- **The problem:** both jobs run *only* on the Android device today. [RecurringScheduler](../../app/src/main/java/com/iponlove/app/feature/recurring/domain/usecase/RecurringScheduler.kt) materializes due occurrences; [BudgetAlertWorker](../../app/src/main/java/com/iponlove/app/feature/budgets/worker/BudgetAlertWorker.kt) evaluates alerts. Supabase has **no** cron or edge functions (only `server_rev` triggers, RLS, couple RPCs). A user living on web for two weeks would get **no** recurring transactions generated until they reopen the phone.
- **Key nuance (softens the urgency):** materialized occurrences use **deterministic ids** (`ruleId:date`), so if the web client *also* materializes with the identical id scheme (W2), the two **converge** — no double transactions. So correctness does **not** force a server-side move; it's a choice between (a) porting the date math to JS/TS *twice* (drift risk) vs. (b) centralizing it server-side once (cleaner, but introduces the app's first edge function + cron and a "who's the writer" question under LWW).
- **Open tensions to grill:** confirm-on-arrival vs. auto-post rules (only auto-post could safely be server-materialized without a user action; confirm-required stays a client/user event); budget alerts are inherently a *notification* concern (already routed through the synced inbox, ADR-0053) — does web need its own evaluation, or does moving evaluation server-side unify both clients? Timezone/`asOf` definition across clients (MonthWindow logic).
- **Ties to:** W2 (deterministic-id parity is the enabler either way), ADR-0012 (background job ownership), ADR-0053 (inbox already synced).

---

## W4 — DataStore preferences: decide per-device vs. synced

- **Status:** BOOKED, needs decision (light grill). Per-pref call; some become a synced table/columns (native + web read them), some stay local by design.
- **The problem:** these live only in Android DataStore, so they don't follow the user to web:
  - **Correctly per-device (do NOT sync):** app-lock PIN/biometric, sync cursor, clock offset, sync status, widget session, last-active-user.
  - **Product call (user may expect them to follow):** theme *palette* (note `accent_color` already syncs on `users`; the rest doesn't), navbar layout, global privacy/amount-mask default, notification prefs — incl. the Item 2-4 (native v1.7.1) budget thresholds + per-budget mutes, which were *deliberately* made **local per-device** (ADR-0054) — revisit under a web lens, tutorial/onboarding progress.
- **Open tensions to grill:** which prefs graduate to a synced `user_preferences` table (or columns on `users`)? For the ones that stay local, web just starts at defaults — acceptable, but confirm per pref. Note ADR-0054 explicitly chose local for budget thresholds/mutes on the reasoning that cross-device dup is prevented by slot-name dedup, not by syncing settings — that reasoning predates committing to web as a first-class surface; re-examine.
- **Ties to:** ADR-0014 (personalize is local preview + DataStore), ADR-0054 (local notification prefs), ADR-0053.

---

## W5 — Starter-seeding guard parity

- **Status:** BOOKED — a contract line-item (can fold into W2's grill). No native change expected; it's a rule the web client must honor.
- **The problem:** [SeedStarterDataUseCase](../../app/src/main/java/com/iponlove/app/feature/onboarding/domain/usecase/SeedStarterDataUseCase.kt) upserts starter categories/accounts on deterministic ids (`starter-category:$userId:$key`) and **overwrites in place** — it does *not* itself check tombstones. The guard against re-seeding lives **upstream**, and it checks the **server** ("has this user already onboarded?"), explicitly *not* local emptiness (a comment in MainActivity notes local emptiness would "duplicate-seed a reinstall/second device"). The web client has empty local state by definition — if it naively seeds on first run, it **resurrects starter rows the user already deleted**.
- **What web must do:** replicate the same server-side "already onboarded?" check before seeding, and honor tombstones. Pin the exact guard condition in the contract.
- **Ties to:** W2, ADR-0013 (users-row-first), the deterministic-id merge behavior.

---

## W6 — Image pipeline parity (compression, private buckets, storage RLS, orphans)

- **Status:** BOOKED — mostly a contract line-item + a storage-RLS check (may need a native/Supabase tweak for origin admission). Good news: attachments store a `storage_url` (not a local path) in `note_images`/`transaction_images` (schema.sql:273-297), and files live in **private** buckets (`receipts`, `note-images`, `couple-banners`) fronted by authenticated URLs — so web *can* render them.
- **What web must replicate / what to verify:**
  - Client-side **compression** ([CompressReceiptUseCase](../../app/src/main/java/com/iponlove/app/feature/transactions/domain/usecase/CompressReceiptUseCase.kt)) + the same bucket/path naming convention on upload.
  - **Authenticated access** — private buckets resolve only with the token attached (StorageAuthInterceptor pattern). Confirm storage RLS/policies admit the web origin (CORS + policy).
  - An **orphan-cleanup** story — the Android app sweeps `filesDir/receipts` for compressed files that never got a row (an Android-local concern); web needs its own handling if an upload fails mid-flight.
- **Ties to:** ADR-0043 (SECURITY DEFINER partner-read storage RLS), the couple-banner bucket (v1.7.0 Item 10).

---

## W7 — Web premium purchase path (Play can't sell on web)

- **Status:** BOOKED, needs product + technical grill. Ties tightly to W1.
- **The problem:** entitlement is *readable* on web (the synced column), but Google Play Billing is Android-only — web cannot *sell* Premium. Options: a web purchase provider (e.g. Stripe) that, on success, calls the same validating entitlement-write RPC from W1; or launch web as read-only-premium (buy on the phone, use everywhere); or defer web monetization entirely at first.
- **Open tensions:** PH-market payment methods on web; keeping one entitlement source of truth across two purchase channels; refund/chargeback handling; how the one-time ₱249 model maps to a web checkout; whether a second payment provider is worth it pre-launch.
- **Ties to:** W1 (shared write RPC), ADR-0044, `subscription-paywall-design.md`.

---

## W8 — Web app greenfield foundational design

- **Status:** BOOKED, greenfield, needs a full grill (the big one). Likely spawns its own ADR(s) and possibly its own repo.
- **The problem:** everything about the web client itself is undecided — framework/stack, hosting, auth flows (email+password + Google OAuth *web* flow, email verification, session handling — the native app uses Credential Manager / `linkIdentityWithIdToken`, none of which apply on web), and crucially the **sync-engine port**: does web reimplement the offline-first Room-equivalent + push/pull, or launch **online-only** against Supabase first (simpler, drops the offline story) and add offline later? Reading/writing through the same RLS + redacting views + couple RPCs; reproducing the W2 contract.
- **Open tensions to grill:** offline-first vs. online-first for v1 of web; PWA vs. plain SPA; how much of the native domain/usecase layer conceptually transfers; whether any native ADRs need a "web variant" note.
- **Depends on:** W1–W6 being settled (they shape the schema + contract the web build sits on).

### Pre-grill stack proposal (2026-07-26, not yet locked)

Recommendation to bring into the W8 grill, derived from Love, Ipon's specific constraints (same Supabase backend, the W2 contract, a PH mobile-web-heavy audience on cheap Android phones + flaky networks, the bespoke "Playful Pop" aesthetic — not a stock component look, and a solo AI-assisted build):

| Layer | Pick | Why for this app |
|---|---|---|
| Language | **TypeScript** (non-negotiable) | Type parity with the Kotlin domain; Supabase's `generate_typescript_types` keeps DTOs in lockstep with `schema.sql`; best-supported language for AI codegen. |
| Framework | **Next.js (React)** — primary | Supabase's first-class integration target; serves SEO marketing/legal pages (SSG) + the app shell in one project; best AI-assist quality. *Lighter alt: **SvelteKit*** — smaller bundles (real win on low-end PH phones), less boilerplate, but weaker AI-assist support. |
| Styling / UI | **Tailwind CSS + shadcn/ui** (Radix primitives) | Playful Pop is bespoke — a pre-styled lib (MUI) would fight it; shadcn gives unstyled, accessible components restyled to brand tokens; theming maps to CSS variables. |
| Server state | **TanStack Query** | Caching/optimistic updates/background refetch over `@supabase/supabase-js` — mirrors the StateFlow observe+refresh model. |
| Auth | `@supabase/supabase-js` + `@supabase/ssr` | Same Auth backend. **Google SSO on web is a redirect OAuth flow, not Credential Manager** — ADR-0050 is Android-only, different code path, same Supabase identity. |
| Charts | **Recharts** or **visx** | For the Analysis donut / expense-flow (hand-rolled in Compose Canvas natively; visx for equivalent control, Recharts for speed). |
| Validation | **Zod** (+ react-hook-form) | Types the boundary; guards money/date inputs. |
| Hosting | **Vercel** or **Cloudflare Pages**, edge near **ap-southeast-1** | Supabase stays the backend (Singapore, good PH latency) — keep the web edge close too. |
| PWA | Service worker (Vite-PWA / next-pwa) | Installable + offline app-shell — high value for PH mobile-web even before full data-offline. |

**The one decision that actually forks the stack — offline-first vs. online-first (the central open tension above):**
- **Online-first (recommended for web v1):** `@supabase/supabase-js` + TanStack Query straight through the same RLS/views/RPCs. Ships fastest, de-risks Q4; defensible because Android remains the offline-primary client and web is often the wifi-laptop surface. Add a PWA shell for installability even without data-offline.
- **Offline-first (later, or if required):** local **IndexedDB** via **Dexie.js** + a TS **port of the sync engine** (push-dirty / pull-`server_rev` / LWW-by-`updated_at`) — a bigger lift that re-implements the W2 contract in TS.
- Recommendation: launch online-first, treat full offline as a fast-follow on the same framework/styling/state stack (only the data layer changes — add Dexie + the sync port under TanStack Query).

**Non-negotiables regardless of framework choice (dictated by [W2](#w2--freeze-the-cross-platform-contract-determinism--sync-protocol--money-math), not a preference):**
- `decimal.js` (or big.js) for all money math — never native `number`.
- `uuid` package's **v5**, exact namespace `9d8f6c2e-5b1a-4f3d-9e7c-1a2b3c4d5e6f` + the exact name strings — never v4.
- Supabase-generated TS types as the schema source of truth.
- Offset-corrected LWW timestamps — never naive `Date.now()`.

**Explicitly avoid:** a generic third-party sync framework (ElectricSQL, PowerSync, RxDB) if going offline-first — the sync protocol is already defined (deterministic ids, LWW by `updated_at`, redacting-view purges); a third-party engine imposes its own model and would fight the W2 contract. Port the existing protocol onto plain IndexedDB/Dexie instead.

---

*Add new web items below as W9, W10, … — book before building/deciding, grill before locking.*
