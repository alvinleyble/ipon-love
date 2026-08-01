# Love, Ipon — Marketing Site — Claude Code Context

*(Draft seed, handed off from the `ipon-love` repo on 2026-07-31. Review and trim as this project takes real shape — this is a starting point, not a locked spec.)*

## What this is

A small, standalone informational marketing site for **Love, Ipon** (personal brand: PattyWallet) — a couples personal finance + notes Android app for the Philippine market. The site pitches the app, shows it off, and sends visitors to install it. It is **not** the app itself and has no backend logic of its own beyond maybe a future mailing-list form.

## Relationship to the app

- Separate repo from the Android app (`ipon-love`) and from the future PWA (Q4 target, not yet built — see the app repo's `docs/web/` if you ever need that context). Fully decoupled; this ships on its own timeline.
- None of the app's architecture applies here — no Room, no Supabase sync, no ADRs, no entitlement model. Don't import that framing by habit.

## Tech stack

- Static site — Astro or plain HTML/Tailwind (pick one early and update this line once decided).
- Hosting: Vercel or Netlify. Deploy to the free subdomain first (`*.vercel.app` / `*.netlify.app`) — a custom domain is not a blocker and can be attached later.
- Domain: not yet purchased as of handoff.

## Content plan (v1)

- Hero + short pitch
- Feature screenshots — **placeholder until the Play Store asset set stabilizes** (see `brand-context.md` / `links-and-refs.md`)
- Google Play button — **placeholder link**; current build is internal-testing only, not a public listing (see `links-and-refs.md`)
- PWA link slot — placeholder/hidden; the PWA doesn't exist yet
- Minimal SEO/OG tags

See `brand-context.md` for what to actually say, and `links-and-refs.md` for the links to wire up.

## Working with Alvin (carried over — general habits, not app-specific)

- **Never `git commit` or `git push` without explicit permission, each time.** Stage and propose; wait for the go-ahead. Permission for one commit doesn't carry to the next.
- State the model choice (Sonnet is almost always right for a project this size) and effort level, with a one-sentence rationale, before starting build work.
- When presenting options or decisions, explicitly label the recommended one — don't bury it in prose.
- Verify by running (dev server + click through) before calling something done, not by eyeballing code.
- Git commit author: `Alvin <alvinpride.ani@gmail.com>` (his GitHub email — differs from his contact email, `tfvin24@gmail.com`).
- Still pre-launch: nothing about the app is live/public yet. Don't design copy or links around a general-availability launch that hasn't happened.

## Open questions (not yet decided at handoff time)

- Domain name.
- Exact copy/branding voice.
- Whether this repo eventually folds into the future Next.js web-app repo (Q4, once built) or stays permanently separate — a later decision, not now.
