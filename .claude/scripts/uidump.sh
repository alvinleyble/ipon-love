#!/bin/bash
# Collapse the on-device UI-inspection loop into ONE allowlistable command.
#
# Why this exists: driving the app used to take three chained calls per step —
#   adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ \
#     && python3 -c '<parse the XML for text + bounds>'
# Every segment of a compound command must match the allowlist for the whole
# line to pass, and `python3 -c` can never be allowlisted (arbitrary code
# execution). So each of ~80 UI steps in a verification run raised a permission
# prompt, which is what made dispatched /ondevice runs unusable.
#
# The parsing lives here as fixed, reviewed content instead of as a one-liner
# the model composes each time, so `Bash(.claude/scripts/uidump.sh*)` is a safe,
# narrow allowlist entry. See memory `feedback-ondevice-fewer-permission-prompts`
# and `feedback-hooks-not-prose-for-automated-behavior`.
#
# Usage:
#   uidump.sh                      list every element with text/desc + tap point
#   uidump.sh --grep Budget        only rows whose text/desc matches (case-insensitive)
#   uidump.sh --tap "Clear all"    tap the centre of the first match, then exit
#   uidump.sh --xml                print the local XML path and nothing else
#   any of the above with          -s <serial>   to target a specific device
#
# Output is one row per element:  <cx> <cy>  |  <text or content-desc>  [clickable]

set -u

ADB="${ADB_BIN:-adb}"
SERIAL="${ANDROID_SERIAL:-${IPON_LOVE_EMULATOR_SERIAL:-}}"
MODE="list"
NEEDLE=""

while [ $# -gt 0 ]; do
  case "$1" in
    -s)      SERIAL="$2"; shift 2 ;;
    --grep)  MODE="list"; NEEDLE="$2"; shift 2 ;;
    --tap)   MODE="tap";  NEEDLE="$2"; shift 2 ;;
    --xml)   MODE="xml";  shift ;;
    *)       echo "uidump.sh: unknown argument '$1'" >&2; exit 2 ;;
  esac
done

if [ -z "$SERIAL" ]; then
  SERIAL="emulator-5556"
fi

if [ -n "$SERIAL" ]; then
  ADB="$ADB -s $SERIAL"
  LOCAL="/tmp/ui-${SERIAL//[^A-Za-z0-9]/_}.xml"
else
  LOCAL="/tmp/ui.xml"
fi

$ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || {
  echo "uidump.sh: uiautomator dump failed (device asleep, or no window in focus?)" >&2
  exit 1
}
$ADB pull /sdcard/ui.xml "$LOCAL" >/dev/null 2>&1 || {
  echo "uidump.sh: adb pull failed" >&2
  exit 1
}

if [ "$MODE" = "xml" ]; then
  echo "$LOCAL"
  exit 0
fi

export UIDUMP_SERIAL="$SERIAL"
python3 - "$LOCAL" "$MODE" "$NEEDLE" <<'PY'
import html, re, subprocess, sys, os

path, mode, needle = sys.argv[1], sys.argv[2], sys.argv[3]
xml = open(path, encoding="utf-8", errors="replace").read()

rows = []
for node in re.finditer(r"<node\b[^>]*/?>", xml):
    tag = node.group(0)

    def attr(name):
        m = re.search(r'\b%s="([^"]*)"' % name, tag)
        return html.unescape(m.group(1)) if m else ""

    label = attr("text").strip() or attr("content-desc").strip()
    if not label:
        continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if not b:
        continue
    x1, y1, x2, y2 = (int(g) for g in b.groups())
    if x2 <= x1 or y2 <= y1:          # zero-area node, not tappable
        continue
    rows.append((label, (x1 + x2) // 2, (y1 + y2) // 2, attr("clickable") == "true"))

if needle:
    n = needle.lower()
    rows = [r for r in rows if n in r[0].lower()]

if mode == "tap":
    if not rows:
        print("uidump.sh: no element matching %r" % needle, file=sys.stderr)
        sys.exit(1)
    # Prefer an explicitly clickable match; Compose often marks the parent, not the label.
    label, cx, cy, _ = next((r for r in rows if r[3]), rows[0])
    serial = os.environ.get("UIDUMP_SERIAL", "")
    cmd = ["adb"] + (["-s", serial] if serial else []) + ["shell", "input", "tap", str(cx), str(cy)]
    subprocess.run(cmd, check=False)
    print("tapped %r at %d %d" % (label, cx, cy))
    sys.exit(0)

if not rows:
    print("(no text/content-desc elements%s)" % (" matching %r" % needle if needle else ""))
for label, cx, cy, clickable in rows:
    print("%5d %5d  |  %s%s" % (cx, cy, label, "  [clickable]" if clickable else ""))
PY
