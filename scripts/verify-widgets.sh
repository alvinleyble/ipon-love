#!/usr/bin/env bash
#
# verify-widgets.sh — on-device visual check for the home-screen widgets
# (v1.6.7 Item 8 Slice 6j "Playful Pop" reskin, and any later widget change).
#
# CLAUDE.md requires verifying UI by running, not eyeballing. This reproduces the
# manual driving run: (re)install staging, then capture the balance + quick-add
# widgets in light and dark, and exercise the balance-widget reveal eye.
#
# PREREQUISITE (one-time, manual): both widgets must already be pinned to the
# home screen of the connected device/emulator — Glance widgets can't be pinned
# reliably from the shell. Pin them once via the launcher's widget picker:
#   • "Net assets" (balance) — resize it TALL to also see the per-account list
#   • "+ Add Txn" (quick add)
#
# Usage:
#   scripts/verify-widgets.sh              # install + capture light/dark + eye
#   scripts/verify-widgets.sh --no-install # skip the gradle install step
#   OUTDIR=/tmp/wshots scripts/verify-widgets.sh   # custom screenshot dir
#
# All adb subcommands are the bare, allowlisted shapes from the `ondevice` skill.

set -euo pipefail

APP_ID="com.iponlove.app.staging"
OUTDIR="${OUTDIR:-/tmp/widget-verify}"
INSTALL=1
[[ "${1:-}" == "--no-install" ]] && INSTALL=0

mkdir -p "$OUTDIR"

echo "==> checking for a connected device"
device_count="$(adb devices | grep -cw device || true)"
if [[ "$device_count" -eq 0 ]]; then
  echo "ERROR: no device/emulator. Boot one first (see the ondevice skill)." >&2
  exit 1
fi
if [[ "$device_count" -gt 1 && -z "${ANDROID_SERIAL:-}" ]]; then
  echo "ERROR: $device_count devices connected. Pick one with e.g." >&2
  echo "         ANDROID_SERIAL=emulator-5554 scripts/verify-widgets.sh --no-install" >&2
  echo "       (adb honors ANDROID_SERIAL for every call). Serials:" >&2
  adb devices | grep -w device | sed 's/^/         /' >&2
  exit 1
fi
echo "    target: ${ANDROID_SERIAL:-$(adb devices | grep -w device | head -1 | cut -f1)}"

if [[ "$INSTALL" == "1" ]]; then
  echo "==> building + installing staging debug"
  ./gradlew installStagingDebug
fi

shot() {  # shot <name>  → OUTDIR/<name>.png
  local name="$1"
  adb exec-out screencap -p > "$OUTDIR/$name.png"
  echo "    captured $OUTDIR/$name.png"
}

echo "==> restarting app so widgets get a fresh snapshot, then going home"
adb shell am force-stop "$APP_ID"
adb shell am start -n "$APP_ID/com.iponlove.app.MainActivity" >/dev/null
sleep 2
adb shell input keyevent 3   # HOME
sleep 2

echo "==> LIGHT mode"
adb shell cmd uimode night no
sleep 2
shot 01-light

echo "==> DARK mode"
adb shell cmd uimode night yes
sleep 3
shot 02-dark

echo "==> back to LIGHT, then toggle the balance-widget reveal eye"
adb shell cmd uimode night no
sleep 2
# The eye sits at the far right of the amount row. These coords suit the Net
# assets widget pinned top-centre on a 1080-wide device; adjust EYE_X/EYE_Y for
# a different placement (find them via: adb shell uiautomator dump, then grep).
EYE_X="${EYE_X:-685}"
EYE_Y="${EYE_Y:-505}"
adb shell input tap "$EYE_X" "$EYE_Y"
sleep 3
shot 03-revealed
echo "    (re-hiding to restore the masked state)"
adb shell input tap "$EYE_X" "$EYE_Y"
sleep 1

echo
echo "==> done. Review the shots in $OUTDIR :"
echo "    01-light     balance (soft card) + quick-add (accent FAB), light"
echo "    02-dark      both widgets re-tinted for dark mode"
echo "    03-revealed  balance eye toggled → real net-assets + per-account amounts"
echo
echo "    If the eye shot didn't reveal, the tap missed — set EYE_X/EYE_Y and re-run"
echo "    with --no-install (dump the layout: adb shell uiautomator dump)."
