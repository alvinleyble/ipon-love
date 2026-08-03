#!/bin/bash
# PreToolUse gate for Bash. Auto-allows ONLY plain `git add` / `git commit`
# calls, and only while /wrap-force's lock file is armed and fresh.
# Everything else — push, reset, any other git subcommand, any command
# containing shell metacharacters that could smuggle extra work in, or
# add/commit when the lock is absent/stale — falls through untouched,
# so the normal permission prompt still applies.

LOCK="/tmp/ipon-love-wrap-force.lock"
MAX_AGE=600  # seconds; caps how long an armed lock stays honored

input="$(cat)"
cmd="$(echo "$input" | jq -r '.tool_input.command // empty')"

[ -n "$cmd" ] || exit 0

# Reject on any shell metacharacter that could chain in extra commands
# (;, &, |, backtick, command substitution). No exceptions — this is what
# keeps the gate from being an injection vector. (Deliberately NOT
# rejecting < / > : Co-Authored-By trailers legitimately contain
# "<email>", and those chars are inert inside the quoted -m argument.)
if echo "$cmd" | grep -qE '[;&|`]|\$\('; then
  exit 0
fi

# Must be exactly `git add ...` or `git commit ...`, optionally with a `-C`
# pointing at THIS checkout. Never push, reset, clean, branch -D, etc.
# The `-C` path is resolved at runtime rather than hardcoded, so the repo can
# be relocated (or run from a worktree) without silently disarming the gate —
# same ${CLAUDE_PROJECT_DIR} pattern the other hooks in here already use.
# A `-C` naming any OTHER repo falls through to the normal permission prompt.
proj="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
rest="$cmd"
case "$cmd" in
  "git -C "*)
    argpath="${cmd#git -C }"
    argpath="${argpath%% *}"
    [ -n "$proj" ] && [ "$argpath" = "$proj" ] || exit 0
    rest="git ${cmd#git -C "$argpath" }"
    ;;
esac
case "$rest" in
  "git add "*|"git commit "*) ;;
  *) exit 0 ;;
esac

[ -f "$LOCK" ] || exit 0

age=$(( $(date +%s) - $(stat -f %m "$LOCK" 2>/dev/null || echo 0) ))
[ "$age" -lt "$MAX_AGE" ] || exit 0

echo '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow","permissionDecisionReason":"wrap-force: plain git add/commit, no shell metacharacters, lock armed"}}'
