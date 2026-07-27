#!/bin/bash
# PreToolUse gate for the Skill tool. Blocks invoking "wrap" or "wrap-force"
# when the "ondevice" skill ran earlier in this same continuous run of tool
# calls with no genuine stop back to the user in between.
#
# House rule (Alvin, 2026-07-28): after on-device verification, report the
# results in plain text and end the turn there — he decides whether to
# /wrap next, the agent must not auto-chain into it.
#
# A "genuine stop" = an assistant message (grouped by message id) whose
# content blocks are ALL non-tool_use. Claude Code's agent loop only resumes
# after a message like that if a brand new user prompt arrives, so finding
# one after the last ondevice call is proof the user actually got a chance
# to read the report and decide before wrap was invoked. Finding none means
# the agent chained straight through — deny.

input="$(cat)"
skill="$(echo "$input" | jq -r '.tool_input.skill // empty')"
[ "$skill" = "wrap" ] || [ "$skill" = "wrap-force" ] || exit 0

transcript="$(echo "$input" | jq -r '.transcript_path // empty')"
[ -n "$transcript" ] && [ -f "$transcript" ] || exit 0

python3 - "$transcript" <<'PYEOF'
import json
import sys

path = sys.argv[1]
with open(path) as f:
    lines = f.readlines()

last_ondevice_idx = None
for i, line in enumerate(lines):
    try:
        obj = json.loads(line)
    except Exception:
        continue
    msg = obj.get("message")
    if not isinstance(msg, dict) or msg.get("role") != "assistant":
        continue
    content = msg.get("content")
    if not isinstance(content, list):
        continue
    for c in content:
        if (
            isinstance(c, dict)
            and c.get("type") == "tool_use"
            and c.get("name") == "Skill"
            and c.get("input", {}).get("skill") == "ondevice"
        ):
            last_ondevice_idx = i

if last_ondevice_idx is None:
    sys.exit(0)  # ondevice never ran in this transcript — nothing to gate

# Scan forward from that call, grouped by message id, for a genuine stop:
# an assistant message whose content blocks are all non-tool_use.
seen = {}
order = []
for i in range(last_ondevice_idx, len(lines)):
    try:
        obj = json.loads(lines[i])
    except Exception:
        continue
    msg = obj.get("message")
    if not isinstance(msg, dict) or msg.get("role") != "assistant":
        continue
    mid = msg.get("id")
    content = msg.get("content")
    if not mid or not isinstance(content, list):
        continue
    if mid not in seen:
        seen[mid] = []
        order.append(mid)
    for c in content:
        if isinstance(c, dict):
            seen[mid].append(c.get("type"))

# The most recent group is never trusted as proof of a stop by itself: if
# a response bundles a text block with a tool_use (the common shape — a
# short remark followed by the next call), the transcript can have the text
# line written before the tool_use line this very hook invocation is gating,
# making an in-progress call look like a finished, stop-only message. Only
# a group STRICTLY BEFORE the last one can count as a confirmed stop.
for mid in order[:-1]:
    types = seen[mid]
    if types and all(t != "tool_use" for t in types):
        sys.exit(0)  # found a genuine stop after ondevice — wrap is fine

reason = (
    "wrap-after-ondevice gate: the ondevice skill ran earlier in this same "
    "continuous turn with no stop in between. Report the on-device test "
    "results in plain text and end your turn there — Alvin decides whether "
    "to /wrap next; don't chain into it yourself."
)
print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": reason,
    }
}))
PYEOF
