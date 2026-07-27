---
name: fewer-permission-prompts
description: Scan a just-finished on-device testing subagent's transcript for Bash commands that triggered permission prompts, and add the missing patterns to .claude/settings.json's permissions.allow list so the same commands don't prompt next time. Use after ondevice verification completes, or when Alvin asks to stop a recurring permission prompt.
---

# /fewer-permission-prompts — close allowlist gaps after ondevice runs

This project runs on-device Android verification (see the `ondevice` skill) with an allowlisted set of `adb`/`gradle`/`sqlite3`/`emulator` commands in `.claude/settings.json` so it can run without interrupting for approval each tap/screenshot/dump. New command shapes creep in over time (a new adb subcommand, a chained `sleep`, a different flag combo) and each one is a fresh prompt until it's added.

## What to do

1. **Find the transcript to scan.** If invoked right after a `SubagentStop` nudge, the hook context names the `transcript_path` — read that subagent's transcript. If invoked ad hoc by Alvin, use the current conversation's own recent turns, or the JSONL logs in `~/.claude/projects/-Users-lovzay-ipon-love/` (grep by session id or by recency).

2. **Find prompted commands.** Look for Bash `tool_use` entries whose command did *not* match any existing pattern in `.claude/settings.json`'s `permissions.allow` — evidence is the corresponding permission-request/approval turn, or (simplest) grep the transcript's Bash `command` strings and diff each one against the current allow list by hand.

3. **Generalize each command into a pattern, matching the existing style:**
   - Keep the literal, safe prefix (the binary + subcommand + fixed flags), wildcard `*` only the variable tail (coordinates, device serial, file path, package name already fixed to `com.iponlove.app.staging`).
   - If a command is a `;`/`&&`/newline-chained compound (e.g. `adb shell input tap X Y; sleep 1; adb exec-out screencap ...`), remember Claude Code's permission checker evaluates **each segment separately** — every segment needs its own allowlist pattern, not just the whole chain. A bare `sleep 1` needs `Bash(sleep *)` even if the adb calls around it are already covered.
   - Never widen scope beyond this project's on-device testing surface (adb/gradle/sqlite3/emulator, scoped to `com.iponlove.app.staging` and `/tmp` or `/private/tmp` paths). Don't add broad catch-alls like `Bash(adb *)` or `Bash(*)`.

4. **Edit `.claude/settings.json` directly** (Edit tool), inserting the new pattern(s) into the existing `permissions.allow` array near similar entries. Dedupe — don't add a pattern that's already covered by an existing one.

5. **Report what was added**, one line per new pattern, so Alvin can see the diff without having to re-read the file.

## Notes

- This skill only ever *adds* allow patterns — it never removes or loosens existing entries, and never touches `deny` rules.
- Don't invent a fix for a prompt you haven't actually seen in a transcript — this is reactive (close gaps that happened), not proactive (guessing what might prompt).
- If nothing in the scanned transcript was missing from the allowlist, say so plainly instead of adding a redundant pattern.
