---
name: ondevice
description: Build, install, launch, drive the UI, and inspect the on-device DB for Love, Ipon on an emulator or Alvin's real device — the project-specific verification recipe, with hard-won gotchas. Use when a slice needs verifying by running (CLAUDE.md requires running, not eyeballing), or when Alvin asks to test/drive/screenshot the app. The adb/emulator/sqlite3 commands here are allowlisted, so it runs without permission prompts.
---

# /ondevice — verify by running

CLAUDE.md requires verifying UI changes by running the app, not eyeballing code. This is the project's driving recipe. The needed `adb`, `emulator`, and `sqlite3 /tmp/*` commands are allowlisted in `.claude/settings.json`, so a normal run prompts for nothing.

**Flavor:** always the **staging** flavor — applicationId `com.iponlove.app.staging`, launch component `com.iponlove.app.staging/com.iponlove.app.MainActivity`. (The `namespace`/class is `com.iponlove.app`; only the applicationId carries the `.staging` suffix.)

**Env:** JDK + SDK are already configured for Alvin's terminal (`build-run-environment` memory). `adb`/`emulator` are on PATH (set in `.zshrc`, inherited by the Bash tool) — always call them bare. Never fall back to the full `$HOME/Library/Android/sdk/...` path or an `export PATH=...;` prefix: most subcommand shapes are only allowlisted in their bare form, so a full-path or PATH-prefixed call silently drops out of the allowlist and prompts.

## Build + install

- `./gradlew installStagingDebug` (builds + installs in one step). Or `assembleStagingDebug` then `adb -s $SERIAL install -r` the APK.

## Device selection — environment-based, explicit `-s` everywhere

**Always resolve the target serial from the environment before issuing any `adb` or `uidump.sh` call:**

```
SERIAL="${ANDROID_SERIAL:-${IPON_LOVE_EMULATOR_SERIAL:-emulator-5556}}"
```

Then pass `-s $SERIAL` to every `adb` command and `uidump.sh` call. Never rely on the default single-device ADB resolution.

### Device allocation rules

| Caller | Device | Serial |
|---|---|---|
| Alvin (interactive / captain) | `Medium_Phone` | `emulator-5554` |
| Dispatched workers / pipeline agents | `Medium_Phone_2` | `emulator-5556` (hard default) |

- **`emulator-5554` (`Medium_Phone`) is the captain's device.** Dispatched workers MUST NOT target it.
- **`emulator-5556` (`Medium_Phone_2`) is the dispatched-worker default.** Same geometry as `Medium_Phone` (1080×2400, density 420, `android-37.0 google_apis_playstore_ps16k arm64-v8a`). Tap and screenshot coordinates from any `Medium_Phone` verification carry over unchanged.
- **`Pixel_Tablet` is permanently forbidden** for any dispatched or automated run — different geometry (coordinates do not transfer).
- An override (e.g. Alvin explicitly sets `ANDROID_SERIAL=emulator-5554`) wins; the default is `emulator-5556`.

### uidump.sh serial handling

`uidump.sh` reads `$ANDROID_SERIAL` / `$IPON_LOVE_EMULATOR_SERIAL` on startup and defaults to `emulator-5556` if neither is set. You can still pass `-s <serial>` explicitly. See the script header for the full precedence.

## Emulator — booting `Medium_Phone_2` (dispatched default)

- **Headless by default (dispatched runs).** Only boot headed (`-no-window` omitted) when Alvin explicitly requests it so he can watch.
- **Fast-boot from the `loggedin` snapshot** instead of paying a cold boot + fresh login on every dispatch:
  - **One-time setup** (or when the snapshot is missing/stale — app data has drifted too far from a clean baseline): cold-boot headed, log in with one of the `test-account-credentials` accounts, then `adb -s emulator-5556 emu avd snapshot save loggedin`.
  - **Every normal dispatched run** (headless, as a `run_in_background` Bash task):
    ```
    emulator -avd Medium_Phone_2 -no-snapshot-save -snapshot loggedin -no-boot-anim -no-window
    ```
    `-no-snapshot-save` protects the snapshot — nothing the test run mutates gets written back, so `loggedin` stays a clean baseline for the next dispatch.
  - **Headed run** (Alvin requested it explicitly — omit `-no-window`):
    ```
    emulator -avd Medium_Phone_2 -no-snapshot-save -snapshot loggedin -no-boot-anim
    ```
  - If a scenario specifically needs a fresh/unauthenticated device (onboarding, first-login flows), cold-boot without `-snapshot`.

- **Wait for boot** as a second `run_in_background` Bash task, using this **exact** command (allowlisted verbatim — do not rephrase; a different string won't match):
  ```
  until adb -s emulator-5556 shell getprop sys.boot_completed | grep -q 1; do sleep 2; done
  ```
  Or, if `$SERIAL` is already set in the environment: `until adb -s $SERIAL shell getprop sys.boot_completed | grep -q 1; do sleep 2; done` (also allowlisted via `adb -s * shell getprop *`).

- Always verify the device is present before booting: `adb -s emulator-5556 shell getprop sys.boot_completed` — if it responds `1`, skip the emulator launch entirely.

## Launch

- `adb -s $SERIAL shell am force-stop com.iponlove.app.staging` then `adb -s $SERIAL shell am start -n com.iponlove.app.staging/com.iponlove.app.MainActivity`.
- Confirm it's up: `adb -s $SERIAL shell dumpsys activity activities | grep ResumedActivity` shows the app. The first `screencap` after launch often catches the splash — wait ~2s and re-shoot.
- Screenshot: `adb -s $SERIAL exec-out screencap -p > /tmp/x.png`, then Read it. PNG pixels == device coords 1:1 — check the actual resolution with `adb -s $SERIAL shell wm size` rather than assuming (differs by AVD; don't carry over a tablet's dimensions onto `Medium_Phone`).
- **Don't `Read` every screenshot back into context.** `uidump.sh -s $SERIAL`'s text dump is cheap and confirms state between steps; only `Read` a PNG when verifying something inherently visual (layout, color, a rendered chart) that the text dump can't tell you.

## Drive the UI — DON'T eyeball coordinates

- Small `FilterChip`s / dialog buttons get missed by guessed taps. **Use `.claude/scripts/uidump.sh`** to read the hierarchy, then tap by coordinate:
  - `.claude/scripts/uidump.sh` — every element with text/content-desc, printed as `<cx> <cy> | <label> [clickable]`. This bare form (no args) defaults to `emulator-5556` as the serial — use it without `-s` for dispatched runs.
  - `.claude/scripts/uidump.sh --grep Budget` — filter to matching labels (case-insensitive).
  - `.claude/scripts/uidump.sh -s emulator-5556 --grep Budget` — explicit serial (same result as bare form in dispatched context).
  - Read the `<cx> <cy>` for your target off the dump, then tap with `adb -s $SERIAL shell input tap <cx> <cy>` — a true wildcard (`Bash(adb -s * shell input *)`) that never needs a new allowlist entry.
  - **Do NOT use `--tap "<label>"`.** It looks covered by a general wildcard in `.claude/settings.json` but isn't — in practice it prompts fresh for *every distinct label string* (confirmed 2026-07-27/28). Dump + coordinate-tap is the only combination that's actually prompt-free.
- **Do NOT hand-roll the old three-step loop** (`uiautomator dump && adb pull && python3 -c '…'`). Every segment of a compound command must match the allowlist for the line to pass, and `python3 -c` can never be allowlisted.
- **keyevent 4 (Back) gotcha:** it closes the *dialog* if the soft keyboard isn't actually up. Only use it to dismiss a keyboard you're sure is shown. Otherwise the Save button sits just above the keyboard (~y 1790 on these dialogs) — tap it directly.
- Account/category/budget/transaction editors are `AlertDialog`s; the pickers inside are scrollable `FilterChip` rows.
- **Check for silent crashes/ANRs, not just visible failures.** After driving a scenario, before declaring it a pass: `adb -s $SERIAL logcat -d | grep -E "FATAL EXCEPTION|ANR in"`.

## Seed test data via the DB — don't tap it in

Building a scenario through the UI costs ~30 taps each. Seed the **server** instead, then pull-to-refresh in the app: the client still pulls the rows and still recomputes everything, so the logic under test is untouched — only the data entry is skipped.

- `supabase/seeds/budget_alert_scenario.sql` — puts one budget at an exact % spent, for budget-alert/notification work. Edit the params block at the top, run it through the Supabase MCP (`execute_sql`), then pull-to-refresh. Re-running replaces rather than accumulates.
- Same trick generalises: for any scenario needing pre-existing rows, write the SQL rather than driving the UI. Reserve UI driving for the behaviour you're actually verifying.

## Supabase MCP

The Supabase MCP is registered **globally / workspace-wide** (`ipon-love-supabase-mcp-reregister` memory). Dispatched workers inherit it automatically — no per-worktree re-registration is needed. The `execute_sql` tool is available in any dispatched agent session that has MCP tools enabled.

## Inspect the on-device DB

- Device has no `sqlite3` and the DB is WAL. Pull all three files with `dd` under `run-as` (not `cat`/`exec-out` — they corrupt binary). Issue **three separate `adb` calls, never a `for` loop** — a loop's command string starts with `for`, not `adb`, so it can't match the allowlist and prompts every time:
  - `adb -s $SERIAL shell run-as com.iponlove.app.staging dd if=databases/ipon.db 2>/dev/null > /tmp/ipon.db`
  - `adb -s $SERIAL shell run-as com.iponlove.app.staging dd if=databases/ipon.db-wal 2>/dev/null > /tmp/ipon.db-wal`
  - `adb -s $SERIAL shell run-as com.iponlove.app.staging dd if=databases/ipon.db-shm 2>/dev/null > /tmp/ipon.db-shm`
- Query with **macOS** `sqlite3 /tmp/ipon.db "select ..."` — the `-wal` file holds the most recent writes. Confirms `pending_sync`, stamped `updated_at`, `userId`, etc.

## local.properties — worker copy access

`local.properties` holds the Supabase URLs, anon keys, and Google OAuth client ID required for the staging build. It is git-ignored (contains credentials) and must be present for Gradle to configure.

**For dispatched workers in a git worktree:** `local.properties` is **shared from the primary checkout** via the worktree mechanism — the worktree's working directory overlays the same files as the primary checkout, and the git-ignored `local.properties` from the primary checkout (`kun-agent-workspace/projects/ipon-love/local.properties`) is visible here at the worktree root. No copy or population step is needed as long as the primary checkout has it.

**If `local.properties` is missing in a new checkout or CI environment:** Gradle will fail at configure time (`file("") ...` crash). Populate it from the known credentials:
```
sdk.dir=/Users/lovzay/Library/Android/sdk
STAGING_SUPABASE_URL=<from memory ipon-love-supabase-mcp-reregister or Supabase dashboard>
STAGING_SUPABASE_ANON_KEY=<anon key>
STAGING_GOOGLE_WEB_CLIENT_ID=<from memory>
```
The anon key is safe to store locally (it's the public API key, not the service role key). See `CLAUDE.md` for the `AGENTS.md` instruction to always read `docs/build/project-build-progress.md` for current schema, not this file, for schema questions.

## Accounts

- Use a real paired staging account for couples/sharing flows (`test-account-credentials` memory: `testdev2–5@iponlove.com`, and `testdev15@iponlove.com` is a paired couple). Re-login is needed each fresh emulator boot. The `loggedin` snapshot on `Medium_Phone_2` should be saved with one of these already signed in.

## Logs

- On Alvin's **real device** (Transsion/MTK), app `Log.d` is dropped from logcat (`device-logd-suppressed` memory). Use `Log.i`/`Log.w` for anything you need to read back via `adb logcat`.

## Report format (dispatched runs)

When this skill runs inside a dispatched subagent (`ondevice-model-dispatch` memory), close with a fixed-shape report instead of free prose — Alvin isn't watching live, so this needs to be scannable without re-reading the transcript:

- **Scenario:** what slice/behavior was under test
- **Device:** serial used (e.g. `emulator-5556`) and AVD name
- **Golden path:** pass/fail + what was checked
- **Edge cases:** pass/fail per case checked
- **DB check:** query + result, if one was run
- **Crash/ANR check:** clean, or paste the matching logcat line(s)
- **Screenshots:** file paths only (don't re-embed) unless something looks visually wrong and needs Alvin's eyes

## Notes

- Alvin usually self-tests each slice on his own device before commit (`feedback-alvin-self-tests-on-device`). When he's driving, this skill is for reproducing/debugging, not gating — don't assume the emulator loop is the gate.
- This skill is the single source of truth for the recipe — don't let a memory grow a second copy of it (that's how the `for`-loop bug above resurfaced once already).
