# Reference Links

- **Play Console internal testing opt-in:** https://play.google.com/apps/internaltest/4701535184050376224 — testing-only. Requires the visitor's Google account email to already be added as a tester in Play Console; they open the link signed into that account, then tap "Become a tester" → "Download it on Google Play." **Do not wire this up as the public "Get it on Google Play" button** — it's a placeholder until the app has a real public listing.
- **Screenshot assets:** live in the app repo at `assets/brand/playstore/` (with a `render.sh` render pipeline + `slides.css` design system). That set is still being iterated as app UI changes land — don't copy it in yet. Ask for a fresh export when it's time to build the real feature-screenshot section.
- **PWA:** doesn't exist yet (Q4 target in the app repo, architecture not even finalized). Nothing to link the PWA slot to — leave it a dead/hidden placeholder.
