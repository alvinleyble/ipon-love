#!/bin/bash
# PreToolUse gate for Edit/Write. While a /book turn is armed (see
# book-lock-arm.sh), blocks any write outside docs/ — /book only ever
# writes the item stub into docs/build/vX.Y.md; touching app source in
# the same turn is exactly the mistake feedback-book-lists-only-no-autobuild
# was written to prevent. Real code changes go through freely once the
# next user message (any content) has disarmed the lock.

input="$(cat)"
sid="$(echo "$input" | jq -r '.session_id // "default"')"
path="$(echo "$input" | jq -r '.tool_input.file_path // ""')"

lockfile="${CLAUDE_PROJECT_DIR:-.}/.claude/.hook-state/book-lock-$sid"

[ -f "$lockfile" ] || exit 0

case "$path" in
  */docs/*|docs/*)
    exit 0
    ;;
esac

echo '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"Blocked by /book enforcement: this turn was triggered by /book, which only books an item stub under docs/build/ and must not touch code or other files. Send a separate message to build it."}}'
