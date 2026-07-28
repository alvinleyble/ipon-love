#!/bin/bash
# SubagentStop hook. When a dispatched ondevice-testing subagent finishes its
# turn NATURALLY (stop_reason == end_turn) and its transcript shows it drove
# on-device testing (adb/gradlew/sqlite3 commands present) but never ran the
# crash/ANR check from the ondevice skill (`adb logcat -d | grep -E "FATAL
# EXCEPTION|ANR in"`), nudge the main conversation: a tap that no-ops on
# screen can look identical to one that crashed the activity underneath a
# respawn, so a "pass" reported without this check may be hiding a crash.
# Never fires for subagents that weren't doing on-device work, and never
# fires on anything but a completed run.

input="$(cat)"
stop_reason="$(echo "$input" | jq -r '.stop_reason // empty')"
[ "$stop_reason" = "end_turn" ] || exit 0

transcript="$(echo "$input" | jq -r '.transcript_path // empty')"
[ -n "$transcript" ] && [ -f "$transcript" ] || exit 0

grep -qE '"command":"(adb |\./gradlew |sqlite3 )' "$transcript" 2>/dev/null || exit 0

grep -q 'FATAL EXCEPTION' "$transcript" 2>/dev/null && exit 0

echo '{"hookSpecificOutput":{"hookEventName":"SubagentStop","additionalContext":"The ondevice-testing subagent just completed normally, but its transcript shows no crash/ANR check (`adb logcat -d | grep -E \"FATAL EXCEPTION|ANR in\"`) was ever run. A tap that no-ops on screen can look identical to one that crashed the activity underneath a respawn — the scenario may have been declared a pass without ruling that out. Consider running the check now against the still-live device, or flag this gap to Alvin before trusting the report."}}'
