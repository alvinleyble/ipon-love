# Notes pin is a synced `is_pinned` boolean on the note row, hoisting into a "Pinned" section

## Context

Beta feedback (V1.6.3 Item 1): Notes has no way to pin a note to the top of the list. Notes currently sort purely by recency — `ORDER BY updatedAt DESC, createdAt DESC` (`NoteDao.observeNotes`) — with no manual reorder (unlike accounts/categories, which carry an explicit `sortOrder`).

Two things make this non-trivial in *this* app rather than a generic notes app:

1. **Notes can be shared.** A note has one owner and is shared one-directionally (`is_shared` + `couple_id`). Partner notes are read-only replicas surfaced via the redacting view (ADR-0005). So "pin" has to answer: pin *whose* notes, and does a pin *propagate* to the partner?
2. **The sync engine replaces whole rows.** `NoteDao.applyPullBatch` uses `OnConflictStrategy.REPLACE`, so every pull overwrites the entire local row.

## Decision

**Pin is a synced `is_pinned` boolean column on the `notes` row. Multiple pins allowed. Pinned notes hoist into a "Pinned" section at the top; ordering is otherwise unchanged.**

1. **Synced column, not a local/per-user preference.** Add `is_pinned boolean not null default false` to `notes` (schema + `NoteEntity` → Room version bump + migration + `NoteDto`/`NoteMapper` and the partner variants). Pin/unpin reuses the **existing** `UpsertNote` write path — it stamps `updated_at` + `pending_sync` and syncs by LWW like every other column. No new sync logic.

   *Rejected: local-only per-user pin* (a non-synced Room column, or a separate `note_pins` table / DataStore set). It would let each partner pin anything independently (including partner notes) with no propagation — but it fights the pipeline: a non-synced column gets **clobbered on every `applyPullBatch` REPLACE**, so surviving a sync requires a separate store + a join in the observe query + delete reconciliation. Strictly more code and a correctness trap, to buy a capability (privately pinning a partner's note) that isn't worth it for a polish feature.

2. **Consequences of it being a row column, accepted deliberately:**
   - You can only pin notes **you own** — partner notes are read-only replicas (ADR-0005), so they have no writable pin. Minor: partner notes are a small slice already distinguished by the "From {Partner}" label.
   - Pinning a note you've **shared** propagates the pin to your partner's replicated copy. Defensible — a shared note important to one partner is plausibly important to both, and pin is a lightweight signal.

3. **Multiple pins, not single.** Matches Keep/Apple Notes/Samsung Notes; also the simpler data model (a boolean per note, no "which one is THE pin" bookkeeping).

4. **Boolean, not `pinned_at` timestamp.** The query becomes:
   ```sql
   ORDER BY isPinned DESC, updatedAt DESC, createdAt DESC
   ```
   Pinning only *hoists* a note into the top group; order **inside** each group stays "most recently edited," identical to today (Apple Notes behaves this way). We don't need pin-time ordering; if "sort by when I pinned it" is ever wanted, that's a later change — don't pay for it now.

## Consequences

- One new synced column with a Room migration; the DTO/mapper touch is the well-worn "new column on an existing synced entity" path (users → … → notes ordering per ADR-0009 unchanged).
- Pin state survives reinstall for free via sync (a local-only design would not).
- **Tier-1 tests:** the sort ordering (pinned-hoist + within-group recency) and the mapper round-trip for the new column, per the money/sync testing policy analogue for list-ordering logic. Pin/unpin is just an `UpsertNote`, already covered by the write path.
- UI: a "Pinned" section header in `NotesScreen`, a pin/unpin affordance (row overflow or editor action). The existing note-title row already uses `weight(1f, fill=false)` + ellipsis, so adding the `SharedBadge` (V1.6.3 Item 9) alongside a pin indicator won't truncate.

## Suggested build

Sonnet, medium effort — follows the established add-a-synced-column slice pattern (entity → migration → DTO/mapper → DAO order-by → UI section). The only judgment already spent here is the synced-vs-local call, which this ADR settles.
