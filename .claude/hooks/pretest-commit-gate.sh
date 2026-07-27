#!/bin/bash
# PreToolUse gate for Bash. Before any `git commit` whose staged changes
# include app Kotlin source, runs the JVM unit test suite and blocks the
# commit if it fails. Docs-only commits (no staged app/src/**/*.kt changes)
# skip the run entirely — this only guards CLAUDE.md's testing-policy gate
# ("build compiles green, domain + data logic has unit tests"), which used
# to live only as a step in the /wrap skill's prose and was skippable by
# committing directly instead of going through /wrap.
#
# This hook only ever denies (on test failure) or stays silent (tests pass,
# or nothing staged to test) — it never auto-allows. wrap-force-gate.sh's
# "allow" (or the normal permission prompt) still applies on top once this
# hook has no objection; a deny from here takes precedence either way.

input="$(cat)"
cmd="$(echo "$input" | jq -r '.tool_input.command // empty')"

[ -n "$cmd" ] || exit 0
echo "$cmd" | grep -Eq '(^|;|&&)[[:space:]]*git( -C [^ ]+)? commit\b' || exit 0

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

staged="$(git diff --cached --name-only 2>/dev/null)"
echo "$staged" | grep -Eq '^app/src/.*\.kt$' || exit 0

# macOS ships no `timeout` — it's `gtimeout` from coreutils, and only if installed.
# Calling it unconditionally exits 127, which this gate would have read as "tests
# failed" and used to block every commit. Degrade to running untimed rather than
# denying on a missing binary.
if command -v timeout >/dev/null 2>&1; then
  TIMEOUT="timeout 300"
elif command -v gtimeout >/dev/null 2>&1; then
  TIMEOUT="gtimeout 300"
else
  TIMEOUT=""
fi

out="$($TIMEOUT ./gradlew testStagingDebugUnitTest 2>&1)"
status=$?

if [ "$status" -eq 124 ]; then
  jq -n '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:"Pre-commit test gate: ./gradlew testStagingDebugUnitTest timed out after 5 minutes — commit blocked. Investigate manually, then retry."}}'
  exit 0
fi

if [ "$status" -ne 0 ]; then
  tail="$(echo "$out" | tail -40)"
  reason="Pre-commit test gate: unit tests failed — commit blocked. Run ./gradlew testStagingDebugUnitTest to see full output, fix the failures, then retry the commit.

${tail}"
  jq -n --arg reason "$reason" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$reason}}'
  exit 0
fi

exit 0
