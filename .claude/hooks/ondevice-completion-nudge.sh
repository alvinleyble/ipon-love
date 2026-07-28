#!/bin/bash
# SubagentStop hook. When a dispatched ondevice-testing subagent finishes its
# turn NATURALLY (stop_reason == end_turn — not a mid-run halt/cancel, which
# either doesn't fire this event or would show a different stop_reason), and
# its transcript shows it actually ran adb/gradlew/sqlite3 commands (evidence
# of on-device work, not just any subagent), nudge the main conversation to
# invoke the fewer-permission-prompts skill so any command that had to prompt
# for approval during the run gets allowlisted before the next dispatch.
# Broadened beyond bare adb because a run whose only friction was a gradle or
# sqlite3 command shape (no adb at all) would otherwise never trigger this.
# Never fires for subagents that weren't doing on-device work, and never
# fires on anything but a completed run.

input="$(cat)"
stop_reason="$(echo "$input" | jq -r '.stop_reason // empty')"
[ "$stop_reason" = "end_turn" ] || exit 0

transcript="$(echo "$input" | jq -r '.transcript_path // empty')"
[ -n "$transcript" ] && [ -f "$transcript" ] || exit 0

grep -qE '"command":"(adb |\./gradlew |sqlite3 )' "$transcript" 2>/dev/null || exit 0

echo '{"hookSpecificOutput":{"hookEventName":"SubagentStop","additionalContext":"The ondevice-testing subagent just completed normally. Invoke the fewer-permission-prompts skill now, pointed at this subagent'"'"'s transcript, to close any allowlist gap it hit."}}'
