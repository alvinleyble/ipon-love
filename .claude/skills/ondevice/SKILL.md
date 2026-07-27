---
name: ondevice
description: Build, install, launch, drive the UI, and inspect the on-device DB for Love, Ipon on an emulator or Alvin's real device — the project-specific verification recipe, with hard-won gotchas. Use when a slice needs verifying by running (CLAUDE.md requires running, not eyeballing), or when Alvin asks to test/drive/screenshot the app. The adb/emulator/sqlite3 commands here are allowlisted, so it runs without permission prompts.
---

# /ondevice — verify by running

CLAUDE.md requires verifying UI changes by running the app, not eyeballing code. This is the project's driving recipe. The needed `adb`, `emulator`, and `sqlite3 /tmp/*` commands are allowlisted in `.claude/settings.json`, so a normal run prompts for nothing.

**Flavor:** always the **staging** flavor — applicationId `com.iponlove.app.staging`, launch component `com.iponlove.app.staging/com.iponlove.app.MainActivity`. (The `namespace`/class is `com.iponlove.app`; only the applicationId carries the `.staging` suffix.)

**Env:** JDK + SDK are already configured for Alvin's terminal (`build-run-environment` memory). `adb`/`emulator` are on PATH (set in `.zshrc`, inherited by the Bash tool) — always call them bare. Never fall back to the full `$HOME/Library/Android/sdk/...` path or an `export PATH=...;` prefix: most subcommand shapes are only allowlisted in their bare form, so a full-path or PATH-prefixed call silently drops out of the allowlist and prompts.

## Build + install

- `./gradlew installStagingDebug` (builds + installs in one step). Or `assembleStagingDebug` then `adb install -r` the APK.

## Emulator (only if not using Alvin's real device)

- Only **tablet AVDs** exist (`Pixel_Tablet`, `Medium_Tablet`) unless a phone AVD was added since. `emulator -list-avds` to check.
- Boot dies between sessions — re-boot each time as a **`run_in_background` Bash task**: `emulator -avd <name> -no-snapshot-save -no-boot-anim`.
- Wait for boot as a second `run_in_background` Bash task, using this **exact** command (it's allowlisted verbatim — do not rephrase it, a different string won't match): `until adb shell getprop sys.boot_completed | grep -q 1; do sleep 2; done`

## Launch

- `adb shell am force-stop com.iponlove.app.staging` then `adb shell am start -n com.iponlove.app.staging/com.iponlove.app.MainActivity`.
- Confirm it's up: `adb shell dumpsys activity activities | grep ResumedActivity` shows the app. The first `screencap` after launch often catches the splash — wait ~2s and re-shoot.
- Screenshot: `adb exec-out screencap -p > /tmp/x.png`, then Read it. On the tablet AVD the screen is 1600×2560 portrait; PNG pixels == device coords 1:1.

## Drive the UI — DON'T eyeball coordinates

- Small `FilterChip`s / dialog buttons get missed by guessed taps. **Use `.claude/scripts/uidump.sh`** — it dumps, pulls, and parses the hierarchy in one allowlisted call, so it never prompts:
  - `.claude/scripts/uidump.sh` — every element with text/content-desc, printed as `<cx> <cy> | <label> [clickable]`
  - `.claude/scripts/uidump.sh --grep Budget` — filter to matching labels (case-insensitive)
  - `.claude/scripts/uidump.sh --tap "Clear all"` — find the element and tap its centre in one step (prefers a `clickable="true"` match, since Compose often marks the parent rather than the label)
  - add `-s <serial>` for a specific device
- **Do NOT hand-roll the old three-step loop** (`uiautomator dump && adb pull && python3 -c '…'`). Every segment of a compound command must match the allowlist for the line to pass, and `python3 -c` can never be allowlisted — that loop is what made dispatched runs raise a permission prompt on nearly every UI step.
- **keyevent 4 (Back) gotcha:** it closes the *dialog* if the soft keyboard isn't actually up. Only use it to dismiss a keyboard you're sure is shown. Otherwise the Save button sits just above the keyboard (~y 1790 on these dialogs) — tap it directly.
- Account/category/budget/transaction editors are `AlertDialog`s; the pickers inside are scrollable `FilterChip` rows.

## Seed test data via the DB — don't tap it in

Building a scenario through the UI (create category → create budget → create transaction, repeat) costs ~30 taps each and dominated the cost of the v1.7.1 Item 6 verification run. Seed the **server** instead, then pull-to-refresh in the app: the client still pulls the rows and still recomputes everything, so the logic under test is untouched — only the data entry is skipped.

- `supabase/seeds/budget_alert_scenario.sql` — puts one budget at an exact % spent, for budget-alert/notification work. Edit the params block at the top, run it through the Supabase MCP (`execute_sql`), then pull-to-refresh. Re-running replaces rather than accumulates; cleanup snippet is in the file's header.
- Same trick generalises: for any scenario needing pre-existing rows, write the SQL rather than driving the UI. Reserve UI driving for the behaviour you're actually verifying.

## Inspect the on-device DB

- Device has no `sqlite3` and the DB is WAL. Pull all three files with `dd` under `run-as` (not `cat`/`exec-out` — they corrupt binary). Issue **three separate `adb` calls, never a `for` loop** — a loop's command string starts with `for`, not `adb`, so it can't match the allowlist and prompts every time:
  - `adb shell run-as com.iponlove.app.staging dd if=databases/ipon.db 2>/dev/null > /tmp/ipon.db`
  - `adb shell run-as com.iponlove.app.staging dd if=databases/ipon.db-wal 2>/dev/null > /tmp/ipon.db-wal`
  - `adb shell run-as com.iponlove.app.staging dd if=databases/ipon.db-shm 2>/dev/null > /tmp/ipon.db-shm`
- Query with **macOS** `sqlite3 /tmp/ipon.db "select ..."` — the `-wal` file holds the most recent writes. Confirms `pending_sync`, stamped `updated_at`, `userId`, etc.

## Logs

- On Alvin's **real device** (Transsion/MTK), app `Log.d` is dropped from logcat (`device-logd-suppressed` memory). Use `Log.i`/`Log.w` for anything you need to read back via `adb logcat`.

## Accounts

- Use a real paired staging account for couples/sharing flows (`test-account-credentials` memory: `testdev2–5@iponlove.com`, and `testdev15@iponlove.com` is a paired couple). Re-login is needed each fresh emulator boot.

## Notes

- Alvin usually self-tests each slice on his own device before commit (`feedback-alvin-self-tests-on-device`). When he's driving, this skill is for reproducing/debugging, not gating — don't assume the emulator loop is the gate.
- This skill is the single source of truth for the recipe — don't let a memory grow a second copy of it (that's how the `for`-loop bug above resurfaced once already).
