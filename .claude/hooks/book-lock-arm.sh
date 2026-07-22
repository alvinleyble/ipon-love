#!/bin/bash
# UserPromptSubmit hook. Arms a per-session lock when the raw prompt is a
# /book invocation; disarms it on every other prompt. The lock is what
# book-gate.sh (PreToolUse) checks to block Edit/Write on non-doc files for
# the duration of that turn — /book only ever writes the item stub into
# docs/build/vX.Y.md; touching app source in the same turn is exactly the
# mistake the feedback-book-lists-only-no-autobuild memory was written to
# prevent, now enforced mechanically instead of relying on memory recall.
# The very next user message (any content) disarms it, matching the house
# rule that building happens on a separate, later, explicit instruction.

input="$(cat)"
sid="$(echo "$input" | jq -r '.session_id // "default"')"
prompt="$(echo "$input" | jq -r '.prompt // ""')"

lockdir="${CLAUDE_PROJECT_DIR:-.}/.claude/.hook-state"
mkdir -p "$lockdir"
lockfile="$lockdir/book-lock-$sid"

if echo "$prompt" | grep -qE '^[[:space:]]*/book([[:space:]]|$)'; then
  touch "$lockfile"
else
  rm -f "$lockfile"
fi

exit 0
