# Love, Ipon — Marketing Site

**Charter (set 2026-07-31):** a small, standalone informational marketing site — not the Q4 PWA (see [`../web-build-progress.md`](../web-build-progress.md)), not the native app. Purely: pitch the app, show it off, send visitors to install it. Cold-start orientation for this specific slice; read this file before touching the site repo.

**Status: BUILT (v1), not deployed.** Not grilled — assessed as low risk, no grill needed. Reasoning (per CLAUDE.md's Opus/grill rubric): no cross-ADR interactions, no shared/sync state, no domain logic. It's a static page with a couple of links. Sonnet, low effort, direct build.

Built 2026-08-01 in the separate `alvinleyble/ipon-love-site` repo (`~/ipon-love-site` locally). Astro + Tailwind v4, static output. Verified rendering via local dev server (Hero, Features grid with 8 feature cards, Install CTA) — both light and dark mode via `prefers-color-scheme`. Not yet committed (repo has no commits) or deployed — Vercel/Netlify hookup is the next step, on Alvin's go-ahead.

---

## What this is

An informational one-pager: what the app is, feature screenshots, a button to install. Nothing else — no accounts, no backend calls beyond maybe a mailing-list form later, no shared code with the Android app or the future web app.

## Decisions locked (conversation, 2026-07-31)

- **Separate GitHub repo**, not folded into `ipon-love`. Different stack, different deploy pipeline, no shared code.
- **Stack:** static site (Astro or plain HTML/Tailwind). No React/Next needed for a page this size. **Picked: Astro + Tailwind v4** (2026-08-01) — component slots keep the placeholder Play button / PWA link / screenshot section as clean one-line swaps later, and it's the smoothest eventual fold-in path into the Q4 Next.js repo.
- **Hosting:** Vercel or Netlify. Deploy first to the free subdomain (`*.vercel.app` / `*.netlify.app`) — a custom domain is not a blocker and can be attached later in ~10 minutes once bought.
- **Domain:** not yet purchased. Only reason to buy early is squatting risk on a specific name; otherwise this is the last step, not the first.

## Content (v1)

- Hero + short pitch (what the app is, who it's for)
- Feature screenshots — **placeholder for now**. Real assets are blocked on the Play Store screenshot set stabilizing (still being re-rendered as of late July — don't pull from `assets/brand/playstore/` until that settles).
- **Google Play button** — placeholder link. Play listing is currently internal-testing only (staging-backed, see [play-console-internal-testing]); point this at the real listing once it's public.
- **PWA link slot** — placeholder, dead/hidden. The PWA itself doesn't exist yet (Q4 target, [`../web-phase-0-prep.md`](../web-phase-0-prep.md) W8, architecture not even grilled). Slot exists in the layout now so wiring it up later is a one-line change, not a redesign.
- Minimal SEO/OG tags (title, description, preview image) — this is most of the actual payoff of having a site pre-launch.

## Open items (not blockers, just not decided yet)

- Domain name.
- Exact copy/branding voice.
- Whether this repo folds into the future Next.js web-app repo once W8 is built and its SSG marketing-pages plan becomes real, or stays permanently separate. **Revisit only once W8 ships — not before.**

## Handoff package for the new repo

[`handoff/`](handoff/) — seed context for bootstrapping the new `ipon-love-site` repo's own Claude Code session, so it isn't starting from zero: a draft `CLAUDE.md`, app pitch/feature list for copy, and reference links (Play Console testing link, screenshot-asset location). Distilled 2026-07-31; deliberately excludes anything Android/Kotlin/ADR-specific.

## Relationship to the Q4 web-app track

Independent and unblocked by it. W8's stack notes ([`../web-phase-0-prep.md#w8`](../web-phase-0-prep.md)) already anticipated marketing/legal pages living in the eventual Next.js app-shell repo (SSG). That's a later fold-in question, not a reason to wait — this site ships now, on its own repo/timeline, and either gets absorbed or stays standalone once W8 is real.
