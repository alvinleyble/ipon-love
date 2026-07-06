---
name: wrap
description: Close out a finished vertical slice in the Love, Ipon repo. Use after a slice compiles green and is ready to record — runs the JVM unit tests, updates the build-progress + version docs to house format, stages the diff, and drafts a commit message, then STOPS and asks for commit permission. Never commits or pushes on its own.
---

# /wrap — close out a green slice

Run this when a slice is built and compiling. It performs the post-slice ritual that's easy to forget, and it hard-stops before committing.

## Steps

1. **Run the JVM unit tests** — `./gradlew testStagingDebugUnitTest` (the `stagingDebug` flavor is the one the recent version docs use; fall back to `testDebugUnitTest` if the staging flavor isn't configured for this module). Tier-1 logic (sync, money/budget math, mappers, use cases) must have tests per CLAUDE.md's testing policy. If the slice added such logic without tests, add them before wrapping. Report the actual result — if tests fail, say so and stop.

2. **Update `docs/build/project-build-progress.md`** — edit the "Current state (as of YYYY-MM-DD)" section in place: mark this slice's status (`DONE <sha>` once committed, or `FIXED (uncommitted)` / `built, awaiting on-device test` until then), update the date, and update the version's row in the index table. If the Room schema version changed, update the "Room version" living-reference line too.

3. **Append/update the slice in the in-flight `docs/build/vX.Y.md`** — match the existing item format exactly: a `## Item N — <title>` heading (or update the existing one), then `**Status:**`, `**Severity:**`/`**Suggested model:**`, decisions locked in (reference the ADR if one governs it), **Files touched:**, **Tests added:**, and an on-device verification line once Alvin confirms. Terse, factual, matches the neighbors.

4. **Stage the diff** — `git add` the relevant paths. Show `git status` + a short `git --stat` summary so the user sees exactly what's staged.

5. **Draft the commit message** — conventional-commit subject (`feat:` / `fix:` / etc.) referencing the version item, e.g. `feat: budget rollover (v1.6.2 Item 5)`. Author is Alvin (git identity already configured). End the message body with:

   ```
   Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
   ```

6. **STOP. Ask for commit permission.** HARD RULE (CLAUDE.md + memory): never run `git commit` or `git push` without Alvin's explicit per-time approval. Present the staged diff and drafted message, then wait. Permission for one commit does not carry to the next.

## Notes

- If Alvin hasn't yet tested on-device, the correct status is `FIXED (uncommitted)` / `built, awaiting on-device test` — he self-tests each slice on his real device before committing. Don't mark `DONE` until there's a sha.
- Don't invent a sha. The `DONE <sha>` update happens *after* the commit, on the next turn.
