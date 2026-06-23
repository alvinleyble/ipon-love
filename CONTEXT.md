# Ipon, Love — Domain Context

The shared language of the Ipon, Love couples finance + notes app. This is a glossary, not a spec — it pins down what each term *means* so the code, docs, and conversations agree.

## Language

### Sync

**Sync cursor**:
The pull high-water mark: the max `server_rev` a device has seen, stored per table in DataStore. Pull fetches rows with a greater `server_rev`. (Push uses the Dirty flag, not a cursor.) See ADR-0002.
_Avoid_: last_sync (ambiguous), last_sync_at, watermark

**`server_rev`**:
A server-assigned `bigint` from one global sequence, stamped on every upsert by a trigger. The pull ordering — "what is new to fetch" — kept separate from `updated_at` ("who wins"). Reflects server receipt order, not author edit time.
_Avoid_: revision, version (those imply a per-row counter), sequence

**`updated_at`**:
The client-stamped, offset-corrected wall-clock time of a row's last write. The comparison key for Last-write-wins. Never overridden by a server trigger.
_Avoid_: modified_at, last_modified

**Last-write-wins (LWW)**:
The conflict-resolution rule: when the same row exists on both sides, the version with the greater `updated_at` is kept. Granularity (whole-row vs per-field) is defined in a later decision.
_Avoid_: LWW-by-field (until decided), merge

**Clock offset**:
The delta between this device's wall clock and Supabase server time, captured on each successful sync and applied when stamping `updated_at`, so a skewed device self-corrects toward server time.
_Avoid_: drift, skew (those name the problem, not the stored correction)

**Tombstone**:
A soft-deleted row (`is_deleted = true`) retained — indefinitely in V1 — so the delete propagates through sync. Never hard-deleted (hard-removing one risks resurrection; see ADR-0010). Filtered from all UI queries.
_Avoid_: deleted record, hard delete

**Dirty flag (`pending_sync`)**:
A local-only boolean on every syncable Room row, set on each local write and cleared on a successful push ack. It — not any timestamp — is what selects rows to push. Never sent to Supabase.
_Avoid_: needs_sync, modified flag

**Opening balance**:
The starting balance stored on an Account (`opening_balance`). The only balance figure that syncs; current balance is derived from it plus the ledger, never stored authoritatively. See ADR-0007.
_Avoid_: starting balance, initial balance, balance

**Adjustment transaction**:
A special transaction representing a manual balance correction, so "adjust balance" stays in the ledger rather than overwriting a stored balance. See ADR-0007.
_Avoid_: balance adjustment, correction

### Couples

**Couple**:
The pairing of exactly two Users (user1, user2), identified by a shared invite code and couple name. The unit of data sharing.
_Avoid_: pair, household, family, group

**Invite code**:
The unique code on a Couple that an unpaired user redeems (via the `redeem_invite` RPC) to become user2. Generated/rotated server-side. See ADR-0006.
_Avoid_: pairing code, join code, link

**Combined view**:
The merged, color-coded view of both partners' non-private transactions, plus shared budgets and each partner's monthly spending total. Shows shared *spending*, not partner account balances (which can't be computed correctly past private activity). Distinct from each user's individual view. See ADR-0011.
_Avoid_: shared view, joint view, merged view

**Monthly spending total**:
Per-partner figure in the combined view header (the mockup's chips) = the sum of that partner's *visible* (non-private) transactions for the period. Excludes private activity by design.
_Avoid_: partner balance, net worth, partner total

**Private transaction**:
A transaction marked `is_private = true`. Its content is redacted server-side from partner reads, but its existence + flags still cross the wire so the partner's replica can purge it. See ADR-0005.
_Avoid_: hidden, secret

**Redacting partner view**:
A server-side view (`partner_transactions`, etc.) through which a user reads the partner's rows: always reveals `id`/owner/flags/`server_rev`/`updated_at`, but nulls content columns when the row is private or deleted. The mechanism that lets removals converge. See ADR-0005.
_Avoid_: partner read policy (superseded), shared view

**Purge (local)**:
The client action of deleting a replicated partner row from local Room when it is pulled as a redacted/flagged marker (private, deleted, or unshared). Distinct from a Tombstone, which a user creates for their *own* deletes.
_Avoid_: evict, remove

**Shared record**:
A row owned by the couple rather than one user — a shared budget (`couple_id` set, `user_id` null) or a shared note (`is_shared = true`). Either partner may edit it.
_Avoid_: joint record, couple record (use "shared")

**Conflict copy**:
A new note auto-created when a partner's local edits to a shared note would otherwise be discarded by LWW — the local edits are forked off (title suffixed "(conflict copy — Name)") instead of lost. See ADR-0003.
_Avoid_: conflicted copy, fork, duplicate
