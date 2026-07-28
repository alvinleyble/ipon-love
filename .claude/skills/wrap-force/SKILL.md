---
name: wrap-force
description: Close out a finished vertical slice in the Love, Ipon repo, same as /wrap, but commits both the code and the docs directly instead of stopping for permission at each commit. Use only when Alvin explicitly wants the two-commit ritual run end-to-end without a pause. Still never runs git push on its own.
model: sonnet
---

# /wrap-force — close out a green slice, no commit stops

Identical ritual to `/wrap`, minus the two STOP-and-ask-permission gates. This is enforced by a real mechanism, not just this skill saying so: `.claude/hooks/wrap-force-gate.sh` is a `PreToolUse` hook (registered in `.claude/settings.json`) that auto-allows a Bash call **only** when it's a plain `git add`/`git commit` (optionally `git -C /Users/lovzay/ipon-love`), contains none of `; & | \`` or `$(` (so nothing can be smuggled in after it), **and** the lock file `/tmp/ipon-love-wrap-force.lock` exists and is under 10 minutes old. Everything else — `git push`, any other git subcommand, any command with those metacharacters, or add/commit with the lock absent/stale — falls through to the normal permission prompt, same as any other flow.

(An earlier version of this skill tried to grant the bypass just by saying "don't pause" in prose. That did nothing — the harness's permission gate doesn't read skill instructions, only tool calls and hook decisions. The hook above is what actually does it.)

**Arm/disarm the lock around the whole ritual:**
- **First action, before anything else:** `rm -f /tmp/ipon-love-wrap-force.lock && touch /tmp/ipon-love-wrap-force.lock` (clears any stale lock from an interrupted prior run, then arms a fresh one — both commits below happen under this one lock).
- **Last action, after both commits (or immediately if you stop early — see Notes):** `rm -f /tmp/ipon-love-wrap-force.lock`. Don't leave it armed longer than the ritual needs, even though it self-expires after 10 minutes regardless.

**Commit messages MUST use plain `-m` flags, never a heredoc/`$( )` construction** — the hook explicitly rejects anything containing `$(`, so a heredoc-built message falls through to a normal prompt instead of being fast-pathed. Use one `-m` per paragraph:
```
git commit -m "feat: budget rollover (v1.6.2 Item 5)" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```
Angle brackets in the trailer email are fine — the hook only blocks `;`/`&`/`|`/backtick/`$(`, not `<`/`>`.

**The house workflow is still two commits, in order:** (1) commit the code, (2) update the docs so they reference that code commit's sha, (3) commit the docs. Don't merge them into one commit, and don't stage docs alongside code — the docs commit's `DONE <sha>` line needs the code sha to already exist.

## Phase 0 — verify green

1. **Run the JVM unit tests** — `./gradlew testStagingDebugUnitTest` (fall back to `testDebugUnitTest` if the staging flavor isn't configured for this module). Tier-1 logic (sync, money/budget math, mappers, use cases) must have tests per CLAUDE.md's testing policy. If the slice added such logic without tests, add them before wrapping. If tests fail, stop, disarm the lock, and report — do not commit a red build.

## Phase 1 — commit the code

2. **Stage only the code diff** — `git add` the source/test paths for this slice. Do **not** stage `docs/build/` yet.

3. **Draft the code commit message** — conventional-commit subject (`feat:` / `fix:` / etc.) referencing the version item, e.g. `feat: budget rollover (v1.6.2 Item 5)`. Second `-m` is a `Co-Authored-By` trailer naming the **model that actually built this slice** (per the item's booked Model line — most slices are Sonnet, not Opus): `Co-Authored-By: Claude <model> <noreply@anthropic.com>`.

4. **Commit it.** Run `git commit -m "..." -m "Co-Authored-By: ..."` — no pause (the hook allows it). Report the resulting **sha** for Phase 2.

## Phase 2 — commit the docs (after the code sha exists)

5. **Update `docs/build/project-build-progress.md`** — edit the "Current state (as of YYYY-MM-DD)" section in place: update the date and the short in-flight-version summary (which item is now DONE `<sha>`), and update the version's row in the index table. If the Room schema version changed, update the "Room version" living-reference line too. **Keep "Current state" to a few short sentences — do not append a new narrative paragraph per slice.** The full slice detail (files touched, tests, on-device verification) belongs only in the `vX.Y.md` doc from step 6; this section was trimmed 2026-07-23 after growing to 150+ lines of duplicated prose, and the house rule now is to edit the existing short summary in place, not accumulate.

6. **Append/update the slice in the in-flight `docs/build/vX.Y.md`** — match the existing item format exactly: a `## Item N — <title>` heading (or update the existing one), then `**Status:**` (`✅ DONE <sha>`), `**Suggested model:**`, decisions locked in (reference the ADR if one governs it), **Files touched:**, **Tests added:**, and an on-device verification line once Alvin confirms. Terse, factual, matches the neighbors.

7. **Stage the docs diff** — `git add docs/build/…`.

8. **Draft the docs commit message** — `docs:` subject naming the slice + version item, e.g. `docs: mark v1.6.5 Item 3 done (<sha>)`. Same `Co-Authored-By` trailer as step 3.

9. **Commit it.** Run `git commit -m "..." -m "Co-Authored-By: ..."` — no pause.

10. **Disarm the lock** — `rm -f /tmp/ipon-love-wrap-force.lock`.

11. **Report both shas** to Alvin in one line, e.g. `code a1b2c3d, docs e4f5g6h`, so he can spot-check without having to ask.

## Notes

- **On-device gate still applies.** Alvin self-tests each slice on his real device before committing. If he hasn't tested yet, don't start Phase 1 — the correct interim status is `built, awaiting on-device test`. `wrap-force` removes the commit-permission pause, not the on-device-test gate.
- **Never invent a sha.** Phase 2 uses the actual sha printed by the Phase 1 commit. If the code was already committed earlier, read the sha with `git log -1 --format=%h` rather than guessing.
- **git push is out of scope.** The hook only ever matches `add`/`commit` — push always prompts normally, under `wrap-force` or otherwise.
- If something looks off mid-ritual (tests fail, diff includes unexpected files, unclear which docs item this maps to), stop, disarm the lock, and surface it rather than committing through it — `wrap-force` trades away the permission pause, not judgment.
