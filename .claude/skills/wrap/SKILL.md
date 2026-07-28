---
name: wrap
description: Close out a finished vertical slice in the Love, Ipon repo. Use after a slice compiles green and is ready to record — runs the JVM unit tests, then drives the two-commit ritual (code commit first, then a docs commit that references the code commit's sha). Stages and drafts each, STOPPING for explicit permission before every commit. Never commits or pushes on its own.
model: sonnet
---

# /wrap — close out a green slice

Run this when a slice is built and compiling. It performs the post-slice ritual that's easy to forget, and it hard-stops before every commit.

**The house workflow is two commits, in order:** (1) commit the code, (2) update the docs so they reference that code commit's sha, (3) commit the docs. The docs commit comes second *because* the version docs record `DONE <sha>` — that sha doesn't exist until the code is committed. Don't merge them into one commit, and don't stage docs alongside code.

## Phase 0 — verify green

1. **Run the JVM unit tests** — `./gradlew testStagingDebugUnitTest` (the `stagingDebug` flavor is the one the recent version docs use; fall back to `testDebugUnitTest` if the staging flavor isn't configured for this module). Tier-1 logic (sync, money/budget math, mappers, use cases) must have tests per CLAUDE.md's testing policy. If the slice added such logic without tests, add them before wrapping. Report the actual result — if tests fail, say so and stop.

## Phase 1 — commit the code

2. **Stage only the code diff** — `git add` the source/test paths for this slice. Do **not** stage `docs/build/` yet. Show `git status` + a short `git diff --stat` so the user sees exactly what's staged.

3. **Draft the code commit message** — conventional-commit subject (`feat:` / `fix:` / etc.) referencing the version item, e.g. `feat: budget rollover (v1.6.2 Item 5)`. Author is Alvin (git identity already configured). End the body with a `Co-Authored-By` trailer naming the **model that actually built this slice** (per the item's booked Model line — most slices are Sonnet, not Opus):

   ```
   Co-Authored-By: Claude <model> <noreply@anthropic.com>
   ```

   e.g. `Claude Sonnet 5` for a Sonnet slice, `Claude Opus 4.8` for an Opus one. Don't default to Opus.

4. **STOP. Ask for commit permission.** HARD RULE (CLAUDE.md + memory): never run `git commit` or `git push` without Alvin's explicit per-time approval. Present the staged diff and drafted message, then wait. Once approved and committed, capture the resulting **sha** for Phase 2.

## Phase 2 — commit the docs (after the code sha exists)

5. **Update `docs/build/project-build-progress.md`** — edit the "Current state (as of YYYY-MM-DD)" section in place: update the date and the short in-flight-version summary (which item is now DONE `<sha>`), and update the version's row in the index table. If the Room schema version changed, update the "Room version" living-reference line too. **Keep "Current state" to a few short sentences — do not append a new narrative paragraph per slice.** The full slice detail (files touched, tests, on-device verification) belongs only in the `vX.Y.md` doc from step 6; this section was trimmed 2026-07-23 after growing to 150+ lines of duplicated prose, and the house rule now is to edit the existing short summary in place, not accumulate.

6. **Append/update the slice in the in-flight `docs/build/vX.Y.md`** — match the existing item format exactly: a `## Item N — <title>` heading (or update the existing one), then `**Status:**` (`✅ DONE <sha>`), `**Suggested model:**`, decisions locked in (reference the ADR if one governs it), **Files touched:**, **Tests added:**, and an on-device verification line once Alvin confirms. Terse, factual, matches the neighbors.

7. **Stage the docs diff** — `git add docs/build/…`. Show `git status` + `git diff --stat`.

8. **Draft the docs commit message** — `docs:` subject naming the slice + version item, e.g. `docs: mark v1.6.5 Item 3 done (<sha>)`. Same `Co-Authored-By` trailer as step 3 (the model that built the slice).

9. **STOP. Ask for commit permission** — same hard rule. Present and wait. Permission for one commit never carries to the next.

## Notes

- **On-device gate:** Alvin self-tests each slice on his real device before committing. If he hasn't tested yet, don't start Phase 1 — the correct interim status is `built, awaiting on-device test`. Only proceed to the code commit once he confirms.
- **Never invent a sha.** Phase 2 uses the actual sha printed by the Phase 1 commit. If for some reason the code was already committed earlier, read the sha with `git log -1 --format=%h` rather than guessing.
- If the user prefers to run the commits himself, hand him the staged diff + drafted message at each STOP rather than running `git commit`.
