---
name: orient
description: Cold-start orientation for the Love, Ipon repo. Use at the start of a conversation, or whenever you need to (re)establish where the build is, what's in flight, and which model/effort to use next — before writing any code. Reads exactly the prescribed files and nothing else.
---

# /orient — cold-start orientation

Establish current build state fast and cheaply. The point is to read the **right** files and skip the expensive wrong ones.

## Read these, in order — nothing else

1. `docs/build/project-build-progress.md` — the "Current state" section + the version index table + the "Living reference" block (Room version, infra state, per-feature pattern).
2. The **in-flight version doc** only — pick it from "Current state": whichever `docs/build/vX.Y.md` has uncommitted / TODO / NEEDS DECISION items. Do not read every version doc.
   - **If the in-flight work is the paywall / entitlement**, its design does NOT live in the version doc — also read `docs/build/subscription-paywall-design.md` (§8 feature map, §9 architecture, §10 build spec, §11 grill seams) and ADR-0044. The version doc only lists the paywall *items*; this is their spec.
3. If a specific feature is about to be touched: **one** reference feature folder for the copy-paste pattern (default `app/src/main/java/com/iponlove/app/feature/budgets/`), and the relevant table(s) in `supabase/schema.sql`. Skip this step for pure orientation with no target yet.

## Do NOT read

`PRD.md`, `ARCHITECTURE.md`, `CONTEXT.md`, and do not browse the folder tree. CLAUDE.md + the build docs already cover orientation. Reading these is the main token sink this skill exists to prevent.

## Then report back, tightly

Lead with a table of every item in the in-flight version doc, columns `Item #, Description, Model, Status`. Order the rows: **Done items first, in actual commit order** (chronological — verify with `git log --reverse` against each item's SHA, not by item number or doc position), **then not-yet-done items in build order** (dependency/sequence order). Status is one of: `Done (SHA)`, `Booked`, `For Grilling`, `Ready`. Note any cross-item dependency inline in the Description cell (e.g. "— depends on Item 7").

After the table:
- **Where we are:** one-line state of the in-flight version (what's DONE/committed vs. FIXED-uncommitted vs. NEEDS DECISION).
- **Room version:** the last committed `vNN` (flag if an uncommitted slice bumped it).
- **What's next:** the specific next item, phrased as one actionable line — the top of the build-order table that isn't Done.
- **Model + effort recommendation** for that next item, with a one-sentence rationale — per the Sonnet-by-default / Opus-for-novel-or-cross-ADR rule in CLAUDE.md. State this even if the user hasn't asked yet; it's required before any build.

Don't dump file contents back — synthesize.
