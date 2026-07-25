> **Archived 2026-07-25** — superseded by `CLAUDE.md` and `docs/build/`. Kept for historical reference only.

# Product Requirements Document — Love, Ipon

**Version:** 1.6.5
**Date:** July 2026
**Status:** In beta (internal testing); grilled through grill #5 (entitlement mechanism, ads dropped) and ADRs to 0046

---

## 1. Overview

**Love, Ipon** is an Android personal finance and productivity app designed for couples who want to track individual and shared expenses while staying financially connected. It combines a full-featured budget tracker with a notes system and a couple-sharing layer, wrapped in a clean, aesthetic, highly customizable UI.

The personal instance for the developer is branded **PattyWallet**.

---

## 2. Problem Statement

Existing budget tracker apps (e.g., MyMoney on the Play Store) solve the core tracking problem but are riddled with ads, lack couple-specific features, and offer limited customization. Couples who want financial transparency have no clean, ad-free, aesthetically pleasing tool that supports both individual and shared views of their finances in one place.

---

## 3. Target Users

**Primary:** Filipino couples (ages 20–35) who are financially open with each other and want a shared view of their spending without sacrificing personal privacy.

**Secondary:** Individual users who want a clean, customizable, offline-first budget tracker.

---

## 4. Goals

- Provide a clean, ad-free alternative to existing budget tracker apps
- Enable couples to manage individual and shared finances in one place
- Be offline-first with seamless cloud sync
- Be extensible — architecture must support future AI, vault, and media features without rewrites

---

## 5. Non-Goals (V1)

- iOS version (dropped from roadmap — web dashboard is the planned cross-platform target instead)
- Multi-currency support
- AI chatbot or assistant
- Password vault
- Voice recording storage
- Receipt/photo attachment on transactions *(shipped post-V1)*
- Web dashboard
- CSV / PDF data export

---

## 6. V1 Feature Scope

### 6.1 Authentication & Onboarding

- Email/password registration and login (Supabase Auth)
- Email verification required before first login
- PIN lock on app open
- Biometric unlock (fingerprint / face)
- Profile setup: display name, personal accent color (avatar shown as colored initials/monogram — photo upload is post-V1)
- Couple pairing via generated invite code
- Couple identity: shared couple name (e.g., "PattyWallet"); couple avatar/banner photo is post-V1

### 6.2 Budget Tracker

**Transaction Entry**
- Entry types: Income, Expense, Transfer
- Custom numpad for amount input (same feel as MyMoney)
- Fields: amount, category, account, note/description, date & time
- Option to mark a transaction as private (hidden from partner in combined view)
- Recurring transactions: daily, weekly, monthly, or custom interval

**Accounts**
- Default accounts: Cash / Wallet, Card, Bank, GCash, Maya
- User can add, rename, reorder, or delete accounts
- Per-account balance tracking

**Categories**
- Separate income and expense category lists
- Default categories provided (Bills, Food, Shopping, Transport, Health, Entertainment, Salary, Business, Lottery, Sale, etc.)
- User can add, edit, reorder, or delete categories
- Each category has a customizable icon and color

**Records**
- Transaction list grouped by date, newest first
- Summary bar per period: Total Expense, Total Income, Net Total
- Search transactions by keyword
- Filter by account, category, entry type, date range

**Analysis Views**
- Navigate by day / week / month
- Expense Overview: donut/pie chart by category with percentage breakdown
- Expense Flow: line graph of cumulative spending over selected period
- Calendar view: daily net totals per day of the month

**Budgets**
- Set monthly budget limits per category (e.g., ₱5,000 for Food)
- Budget progress bar per category
- Subtle alert notification at 80% and 100% of limit
- Overall monthly budget overview screen

### 6.3 Couples Features

- **Individual view:** each user sees their own transactions and budgets by default
- **Combined view:** merged transaction list with color-coded attribution per partner (e.g., Alvin in blue, Patty in pink); private transactions excluded
- **Shared budget:** a joint monthly budget both partners contribute to and spend from together
- Couple pairing managed in settings; either partner can unpair

**Partner Debt Tracker** (couples-only; hidden until paired)
- Track informal IOUs between the two partners — e.g., "Alvin borrowed ₱1,000 from Patty"
- Either partner can create a debt record (borrower, lender, original amount, description)
- Partial repayments: log one or more payments against a debt; remaining balance is derived (original − sum of payments)
- A debt is fully settled when remaining balance reaches zero; archived debts can still be reviewed
- Shown as a dedicated Debts section within the Couples view; not visible until paired
- On unpair, all debt records are soft-deleted (same as shared budgets)

### 6.4 Notes

- Create, edit, delete notes
- Rich text: bold, italic, headings, bullet and numbered lists
- Checklist support (to-do style checkboxes)
- Image attachment per note
- Private by default; option to share with paired partner
- Shared notes are visible and editable by both partners

### 6.5 Customization & Themes (Personalize)

- 6 named palettes: Rose, Mauve, Lavender, Peach, Sage, Mocha — **free = Rose + Peach; Premium = all six** (a locked active palette reverts to a free default on any entitlement/enforcement change — chiefly enforcement flip-day, not just refund — non-destructively, remembering the chosen palette for auto-restore; see §7 Monetization and `docs/build/subscription-paywall-design.md` §10.1 / §11 G8)
- Light / dark mode is a separate toggle from palette — 12 combinations total
- Personalize screen: visual swatch grid with live preview before applying
- Couple attribution color (blue vs pink in combined view) is separate from personal theme palette; chosen during the pairing flow
- Post-V1: custom font styles (category/account icon customization shipped in V1.3)

### 6.6 Home Screen Widget

- Small: current balance of a selected account
- Medium: monthly expense / income summary
- Quick-add shortcut: tapping opens transaction entry directly

### 6.7 Notifications (Minimal)

- Budget limit alerts at 80% and 100% thresholds
- Recurring transaction reminder
- No partner activity notifications in v1

---

## 7. Monetization

### Model: One-time purchase — ₱249 Premium (Google Play in-app product)
- **One-time, non-recurring ₱249** purchase unlocks Premium (pivoted 2026-07-07; a brief 2026-07-06 subscription plan was reverted — one-time won on AI-cost + PH-market adoption)
- **Generous free tier** — recording your own money is never gated; Premium sells higher caps, extra palettes, and delighters (calculator, recurring calendar, deep history)
- **No ads** — the app stays ad-free on both tiers (ads dropped 2026-07-08, grill #5; reinforces the ad-free value prop in §1). `NO_ADS` is parked dormant only as a future option, not a launch lever
- The free-vs-premium split and the offline-first entitlement/gating architecture are **designed & grilled** (couples-governance → either-partner-unlocks; lapse → freeze; entitlement cached on the synced `users` row as a client-trusted advisory column — **ADR-0044**). Full spec: `docs/build/subscription-paywall-design.md` (§9–§11); see Post-V1 Horizon in `docs/build/project-build-progress.md`

### Future: AI Add-On
- **A separate add-on, not part of the one-time ₱249 Premium** — ongoing per-call cost can't be funded once-off (paywall doc D8). Positioned as its own thing so "Premium" never implies "unlimited AI."
- **Hybrid credits model:** a starter AI allowance bundled with Premium (goodwill) + **consumable top-up credit packs** (secondary revenue) for continued use, with **BYOK** (bring-your-own-key) in Settings as the power-user escape hatch (zero marginal cost)
- **Server-metered:** paid credits can't be tracked client-side, so AI calls proxy through a Supabase Edge Function that checks/decrements a server-authoritative credit balance before calling the provider (Gemini Flash / Claude Haiku / GPT-4o-mini — chosen at build time). Guardrails: on-device pre-aggregation (compact prompts, keeps financial data local) + per-feature cooldown. Deferred to Horizon #3

---

## 8. Offline & Sync Behavior

- All data written to local Room database first (offline-first)
- Synced to Supabase when a connection is available
- Conflict resolution: last-write-wins per record using timestamp comparison
- Phone change = log in on new device → full data restores automatically; no manual export needed

---

## 9. Future Enhancements (Post-V1)

Reconciled 2026-07-05 against `docs/build/project-build-progress.md` and `ARCHITECTURE.md` (previously out of sync — see that doc's "Post-V1 Horizon" list, now 15 items after the 2026-07-06 monetization addition, re-pivoted to one-time ₱249 Premium 2026-07-07). Target quarters aren't duplicated here — `project-build-progress.md`'s Horizon list is the single source of truth for those.

| Feature | Notes |
|---|---|
| Google Sign-In | Supabase OAuth + Android Credential Manager |
| Facebook Login | Supabase OAuth + Facebook SDK |
| AI financial companion | Hybrid: capped app-funded free tier (cheap model) + opt-in BYOK for unlimited use; spending advice, savings trajectory, friendly assistant persona |
| Password vault | Encrypted local vault |
| Voice recording storage | Extension of the notes system |
| Website / web app | Replaces the earlier iOS-via-KMP idea as the cross-platform target |
| CSV / PDF export | For power users and accountants |
| Custom fonts | Typography customization beyond the built-in color themes (category/account icon customization already shipped in V1.3) |
| Profile & couple photo upload | Avatar / banner images via Supabase Storage |
| Change password / change email while logged in | Settings has no in-app path to either; only the recovery "forgot password" flow exists today, and that requires signing out first |
| Delete my account | Compliance/account-management requirement (likely a Play Store Data Safety item at prod); not a tester-facing feature |
| Login rate limiting / lockout | Client-side cooldown for the Supabase Auth sign-in screen itself, separate from the existing local PIN lockout |
| "Restart fresh" (reset finances) | Wipe transactions, recurring rules, budgets, and goal contributions from Settings without deleting the account or losing accounts/categories/notes; already fully designed (ADR-0037), ready to build |
| Premium paywall + feature gating | One-time ₱249 Google Play in-app purchase for Premium; free-vs-premium split + offline-first entitlement designed & grilled (see `docs/build/subscription-paywall-design.md`) — build infra dormant next, enforce post-beta |
| Display-currency symbol (non-PHP) | Display-symbol-only, NOT multi-currency: swaps the ₱ glyph for another symbol, chosen at onboarding — no per-account currency, no FX conversion, all amounts stay one currency underneath |

---

## 10. Technical Constraints

- **Platform:** Android only (recommend minSdk API 26 / Android 8.0+)
- **Currency:** Philippine Peso (PHP) only in v1
- **Backend:** Supabase (auth, Postgres database, background/triggered sync — not real-time)
- **Local storage:** Room (offline-first)
- **UI:** Jetpack Compose
- **DI:** Hilt
- **Async:** Coroutines + StateFlow
- **Architecture:** MVVM + Clean Architecture, feature-based module structure
- **Language:** Kotlin
