# Delete account is a hard-delete auth-cascade behind a single SECURITY DEFINER RPC

## Context

Google Play's User Data policy makes an in-app account-deletion path (plus a web link to request deletion without reinstalling) a launch blocker — booked as `docs/build/v1.6.5.md` Item 6, grilled 2026-07-12. The design had to reconcile three standing rules:

- **ADR-0010: never hard delete** — every delete is a tombstone (`is_deleted = true`), kept indefinitely, so sync consumers see a deletion instead of a mystery disappearance.
- **ADR-0006/0008: couple ops are server-side RPCs** — and a paired user deleting their account necessarily dissolves the couple, which the partner must survive cleanly.
- **The compliance goal is the opposite of a tombstone:** data actually gone, and the email freed for re-registration.

Facts verified live on staging (2026-07-12, Supabase MCP), not assumed:

- `public.users.id → auth.users(id) ON DELETE CASCADE`, and **every** owned table (`accounts`, `categories`, `transactions`, `budgets`, `notes`, `recurring_rules`, `savings_goals`, `goal_contributions`, `partner_debts` via borrower/lender, `analytics_events`) cascades off `users` — deleting the one `auth.users` row physically removes the entire account. Child tables (`note_images`, `transaction_images`, `partner_debt_payments`) cascade off their parents. GoTrue's own `auth.sessions`/`auth.refresh_tokens`/`auth.identities` cascade off `auth.users` too.
- The couple FK is **asymmetric**: `couples.user1_id → users ON DELETE CASCADE` (deleting the creator hard-deletes the couple row and, transitively, couple-owned rows), `user2_id → SET NULL`.
- The `postgres` role — owner of every existing SECURITY DEFINER RPC (`unpair`, `create_couple`, …) — **has DELETE on `auth.users`** and on `storage.objects`. No service-role edge function is needed to reach admin privilege.
- `storage.objects` has **no FK to `auth.users`** (only `bucket_id`) — the cascade deletes the image *rows* but orphans the actual receipt/note files in the two private buckets.
- Both buckets carry `FOR ALL` owner policies keyed on the `{uid}/` path prefix, so the client can delete its own files via the Storage API with existing RLS.
- `WatchUnpairUseCase` drives the partner-side purge off the partner's **own users row** `couple_id` set→null transition (which `unpair()` writes + restamps in-transaction) — it never depends on pulling the `couples` tombstone.

## Decision

**A departing user is the one sanctioned exception to ADR-0010: `delete_account()` is a `postgres`-owned SECURITY DEFINER RPC that dissolves the couple via the existing `unpair()`, backstops storage, and hard-deletes the caller's `auth.users` row — letting the verified FK cascade physically remove everything, atomically, in one transaction.**

1. **Hard delete, not tombstones — and why it's safe.** ADR-0010's tombstones exist for *other sync consumers*. A departing user has exactly one: the partner — and unpair-first removes them through the normal, tombstone-correct dissolution (revert-to-creator, shared-row soft-deletes, realtime bell, client replica purge). After unpair, the leaver's personal rows are visible to no one else, their own devices get wiped, and their session dies with the cascade. There is no consumer left for a tombstone to inform — so hard delete costs nothing and is what compliance requires. Soft-delete would fail "delete my data" *and* squat on the email in `auth.users`.

2. **One RPC, not an edge function.** Consistent with every existing server op (ADR-0006/0008), no new infra lane, and strictly better atomicity: dissolution + deletion commit or roll back together, so a crash can never leave a dissolved couple with a live account. The only thing an edge function offers — the Admin API — buys nothing the verified `postgres` grant doesn't already give. Shape:

   ```sql
   create or replace function delete_account() returns void
   language plpgsql security definer set search_path = public as $$
   begin
       if auth.uid() is null then raise exception 'not authenticated'; end if;
       if auth_couple_id() is not null then
           perform unpair();   -- reuse the tested dissolution + bell; never inline it
       end if;
       delete from storage.objects
           where bucket_id in ('receipts', 'note-images')
             and name like auth.uid() || '/%';          -- backstop, see (4)
       delete from auth.users where id = auth.uid();     -- cascade removes everything else
   end $$;
   ```

   `unpair()` is **called, not inlined** — it reads `auth.uid()` from the JWT (unaffected by the definer role), and delete inherits its partner-notification + revert-to-creator logic single-sourced.

3. **The creator-deletion cascade race is harmless by construction.** When the leaver is `user1`, the final delete cascade-hard-deletes the `couples` row (and couple-owned tombstones `unpair()` just wrote) *before the partner can pull them*. Safe because partner cleanup is driven by the partner's own users row (`couple_id` → null, pulled normally, surviving the cascade — `user2_id` is SET NULL) feeding `WatchUnpairUseCase` → bulk purge, plus the bell `unpair()` emits pre-commit. The partner lands in exactly the normal-unpair end state. Verified against the client code, not assumed.

4. **Storage: client-first best-effort, RPC SQL backstop.** The client deletes its own files via the Storage API *before* the RPC — it already knows every path offline-first (`note_images.storage_path` + `transaction_images` rows in Room), so it's one batch delete per bucket, no LIST. A failure logs and does **not** abort the deletion. The RPC's `storage.objects` delete then makes anything missed permanently unreachable (every storage API resolves through that table) in the same transaction. Residual physical blobs exist only in the client-failure path, are inaccessible to everyone (private buckets, owner auth gone, partner-read `SECURITY DEFINER` checks key off cascade-deleted DB rows), and are bounded at KB-scale images.

5. **Client gate: online-only + password re-auth, one dialog, no grace period.** Reuses Item 16's (reset finances) exact machinery: `ConnectivityObserver` live-disables the destructive confirm while offline (no offline attempt ever — which also removes the "did the RPC land?" retry-design space), and `AuthRepository.signIn` re-auth is the deliberate-action proof. A single confirm dialog (plain-words warning: everything permanently deleted; if paired, unpaired from {partner} first, their data untouched; irreversible) with the password field inline. No typed-DELETE ceremony, no second dialog, no cooling-off queue.

6. **Client orchestration — the RPC is the point of no return.**
   `cancel WorkManager sync` → `password re-auth` → `storage best-effort` → **`rpc("delete_account")`** → `best-effort signOut()` (the server session is already cascade-dead, so a throw falls back to clearing the local session) → `LocalDataWiper.wipe()` under the existing `NonCancellable` + retry-once wrapper → auth graph.
   Failures before the RPC abort cleanly (account intact, error shown); nothing after it may abort local teardown. **Deliberately no `pushOnly()` first** — unpushed edits are about to be deleted by definition, and a wedged push must never block account deletion (Item 20's lesson).

7. **Analytics history dies with the cascade — correct, and it forbids an "account_deleted" client event** (the row would delete itself). Deletion metrics, if ever wanted, are a server-side count.

8. **Re-registration is free by construction:** the `auth.users` row (where email uniqueness lives) is gone, so the same email can sign up fresh — new verification, new `users` row via `EnsureCurrentUserRowUseCase` (ADR-0013).

9. **The web half of the Play requirement is a manual sub-task, not code:** a static `delete-account.html` on the legal GitHub Pages site (what's deleted, the in-app path, a `mailto:` request contact with a stated ~30-day window, fulfilled manually via the dashboard — the cascade makes that one delete) + the Play Console **Data safety form** URL entry.

## Consequences

- **ADR-0010 gains its one exception**, precisely bounded: hard delete is legal only for a departing user, only after unpair-first, only via this RPC. Every in-app delete remains a tombstone.
- **New synced tables MUST cascade off `users`** (`user_id ... on delete cascade` or transitively via a parent), or account deletion silently starts leaving orphans — this joins the per-feature checklist alongside the ADR-0009 sync ordering.
- **Storage buckets beyond `receipts`/`note-images`** (e.g. future avatars, voice recordings) must be added to both the client best-effort list and the RPC backstop's `bucket_id in (…)`.
- The `couples.user1_id` CASCADE asymmetry is now load-bearing-but-shielded: it only ever fires behind `unpair()`, which has already moved every shared row out of the couple. Changing `unpair()` must keep `delete_account()` correct (it calls it).
- **Naming:** the client slice must dodge the existing *financial* `DeleteAccountUseCase` (`feature/accounts`) — the new use case is `DeleteUserAccountUseCase`.
- If the RPC ever needs to grow (e.g. new cleanup), it stays one transaction — never split into client-sequenced steps.

## Suggested build

One slice (Sonnet — all design calls resolved here; the build is from-spec): migration `2026-07-12_delete_account.sql` (+ `schema.sql` end-state, grants per `unpair()` precedent) · `feature/settings` `DeleteUserAccountUseCase` + small remote/storage ports · ProfileScreen destructive row + Item 16-pattern dialog · JVM tests on the orchestration order (re-auth-failure aborts pre-RPC; storage failure doesn't abort; RPC failure aborts with no wipe; post-RPC teardown proceeds even when signOut throws) · staging test-account walkthrough incl. paired-creator, paired-non-creator, and re-registration.
