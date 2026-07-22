---
name: book
description: Book a pending change as an item in the in-flight version doc BEFORE building it, in the Love, Ipon repo. Use at the start of any new slice/change — states the model+effort choice, records the item stub in the house format, and grills first if the design is novel. The middle of the loop between /orient (start) and /wrap (close).
---

# /book — book a slice before building

Every code change — any size — gets booked as an item in the active `docs/build/vX.Y.md` *before* it's built (per the house rule). This skill enforces that, plus the two things a cold-start agent forgets: stating the model/effort choice, and grilling a novel design first.

Nothing here commits. The item stub stays uncommitted and rides along in `/wrap`'s Phase-2 docs commit once the slice is green.

## Phase 0 — know where you are

1. If you haven't oriented this session, do `/orient` first — you need the **in-flight version doc** (`docs/build/vX.Y.md`) and the **next free Item N** (scan the doc's `## Item ` headings; take the next integer).

## Phase 1 — model + effort (REQUIRED, before any code)

2. **State the model + effort with a one-sentence rationale**, per CLAUDE.md's rule and the `feedback-model-effort-before-build` memory. Sonnet by default; Opus only when the design is genuinely novel, spans multiple ADRs, involves shared/couple state, or you're unsure how it fits before writing code.

## Phase 2 — grill if novel

3. **Decide if it needs a grill.** If the change is a straight pattern-follow (new entity→DAO→syncer→usecase→screen, or a UI/copy tweak), skip. If it's cross-ADR, touches sync/entitlement/couple-shared state, or you can't yet describe the exact shape — run `/grilling` first and book the *grilled* design, not the first guess. Reference the governing ADR (or note a new ADR is needed).

## Phase 3 — write the item stub

4. **Add `## Item N — <title> (<tag>)`** to the in-flight `docs/build/vX.Y.md`, matching the neighbors' format exactly:
   - `- **Status:** PLANNED — booked <today's date>. **Model: <X>, <Y> effort.**` plus a one-line schema/Room note if known (e.g. "no schema change (stays **vNN**)").
   - **Request:** what Alvin asked for, in his framing, with the source (a beta-feedback item, a grill, a bug found during X).
   - **Change:** the concrete plan — files to touch (as clickable `../../app/...` links), which layers, which ADR governs, what explicitly does *not* change.
   - **Verify:** build-green + the on-device check (a paired staging account per `test-account-credentials` when couples/sharing is involved), and whether tier-1 tests are required (sync/money/mapper/usecase → yes; pure Composable → verified by running).
   - If paywall/entitlement-related, cross-link `docs/build/subscription-paywall-design.md` + ADR-0044 rather than restating the design.

5. **STOP HERE.** Do not commit, and do not edit, write, or otherwise touch any non-doc file in this same invocation — not even a one-line fix already fully diagnosed in the stub's Change section. The item stub stays uncommitted as `PLANNED`. Building happens only on a separate, later, explicit instruction (a follow-up message, `/wrap`ing it, or "go build Item N") — never automatically in the turn that booked it.

## Notes

- **New version vs. new item:** if the change is a genuine scope shift (a new feature arc, not the next item in the current batch), start a fresh `docs/build/vX.Y.md` + add its row to the index table in `project-build-progress.md`, instead of appending to the current one.
- Don't duplicate an existing item — if Alvin's ask matches a booked TODO, update that item instead of adding a new one.
