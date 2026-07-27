#!/bin/bash
# PostToolUse reminder for Bash. After a `git commit` runs, checks whether
# docs/build/ was touched in the resulting HEAD commit. If not, nudges to
# update project-build-progress.md's "Current state" and append the slice
# to the active docs/build/vX.Y.md — the cold-start orientation files per
# CLAUDE.md's "after each slice" rule (feedback-update-build-progress memory).
# Reminder only — never blocks, never denies.

input="$(cat)"
cmd="$(echo "$input" | jq -r '.tool_input.command // empty')"

[ -n "$cmd" ] || exit 0
echo "$cmd" | grep -Eq '(^|;|&&)[[:space:]]*git( -C [^ ]+)? commit\b' || exit 0

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

touched="$(git diff-tree --no-commit-id --name-only -r HEAD 2>/dev/null)"
[ -n "$touched" ] || exit 0

if echo "$touched" | grep -q '^docs/build/'; then
  exit 0
fi

echo '{"hookSpecificOutput":{"hookEventName":"PostToolUse","additionalContext":"Reminder: the last commit did not touch docs/build/ — update project-build-progress.md'"'"'s Current state and append this slice to the active docs/build/vX.Y.md (feedback-update-build-progress)."}}'
