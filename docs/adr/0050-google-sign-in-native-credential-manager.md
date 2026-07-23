# Google Sign-In uses native Credential Manager + Supabase ID-token exchange, not the OAuth redirect flow

## Context

Google Sign-In (v1.7.0 Item 2, grilled 2026-07-23) is the first non-email auth pathway. Today there is exactly one: Supabase email+password (`AuthRepositoryImpl`, `AuthScreen`), with no OAuth plumbing to copy. The grill established one architectural fact that collapses most of the apparent complexity:

**Auth is entirely SDK-session-driven.** `AuthRepositoryImpl.status` maps `client.auth.sessionStatus` → `AuthStatus`, and MainActivity's `Authenticated` branch (`accountSwitchGuard.onAuthenticated` → `ensureCurrentUserRow()` → schedule + await `syncEngine.sync()` → `shouldShowOnboarding()`) fires off **session status alone**, never the sign-up path. So a new auth method's entire job is to *make a Supabase session exist*; the ADR-0013 users-row bootstrap, the ADR-0024 onboarding gate (owned categories+accounts empty after first sync), and the ADR-0021 account-switch purge all run unchanged, method-agnostic. The design surface is therefore much narrower than "add OAuth" implies.

The forces:
- Google **deprecated** the legacy Google Sign-In SDK (`GoogleSignInClient`/`startActivityForResult`); **Credential Manager** is its official replacement and the current-recommended native path.
- Supabase documents a **native** flow (`signInWith(IDToken)` with a Google ID token) *and* an **OAuth redirect** flow (`signInWith(Google)` via external browser → deep link). The app already has the `com.iponlove.app://login-callback` deep link registered (recovery + email confirm), so the redirect flow is available at near-zero plumbing cost — but at a browser-hop UX cost on the primary SSO entry point.
- The Horizon origin line named the target explicitly: "Supabase OAuth + **Android Credential Manager**."
- Distribution is via **Play App Signing** (internal testing under `com.iponlove.app`), so the signature Credential Manager validates is Google's re-signing key, not only the local build's.

## Decision

**Google Sign-In uses the native Credential Manager flow: obtain a Google ID token on-device, exchange it via `client.auth.signInWith(IDToken)`. The session that produces flips `sessionStatus`, and the existing session-driven bootstrap takes over with no auth-method awareness anywhere downstream.**

Concretely:

1. **Native, not redirect (Q1).** `androidx.credentials` + `com.google.android.libraries.identity.googleid` drive a `GetGoogleIdOption` request (raw nonce + its SHA-256 hash, per the documented security step); the returned ID token + raw nonce go to Supabase's `signInWith(IDToken)`. The OAuth-redirect/Custom-Tab flow is **rejected for login** — worse UX (browser hop, web account picker) for no gain, since the deep-link plumbing it would reuse isn't a bottleneck.

2. **Hand-rolled thin client, not `compose-auth` (Q2).** A small Android-layer `GoogleCredentialClient` runs the Credential Manager request (needs an **Activity** context — passed through, never stored); `AuthRepository.signInWithGoogleIdToken(idToken, nonce)` makes the Supabase call; a `SignInWithGoogleUseCase` orchestrates; `AuthViewModel.signInWithGoogle(activity)` drives it. This keeps Google sign-in on the **same ViewModel → UseCase → Repository spine** as email/password, so `AuthError` mapping, spinner/lockout state, and testability stay uniform. `compose-auth`'s `rememberSignInWithGoogle` is **rejected** — it puts the auth call inside the Composable, fracturing the layering CLAUDE.md mandates ("UI never accesses Repository/UseCase directly"), to save ~50 lines.

3. **Display name from Google claims via read-time fallback (Q3).** Onboarding has no name-entry step (Welcome → Motif → Currency → Templates → Pair); email users type their name on the register form, Google users have none. Google populates `user_metadata` with `full_name`/`name` (standard OIDC claims), **not** `display_name` (the key `EnsureCurrentUserRowUseCase` reads). Fix: `CurrentUserProvider.displayName()` resolves `display_name ?: full_name ?: name`. **Rejected:** a write-time `updateUser { display_name = … }` normalize — an extra network round-trip and failure surface on every first Google login, versus a side-effect-free one-function read change. Email users are unaffected (their `display_name` is set, so the fallbacks are never consulted).

4. **Automatic account linking, relied upon, not built (Q4).** Supabase auto-links a Google login to an existing account **when both emails are verified**. The app already requires email verification before first login, so every email/password account is verified and Google emails always are — the match lands as the *same user id* (data, couple pairing, all intact; no purge, no account-switch wipe). The known auto-link takeover risk (hijacking an *unverified* account) cannot occur here precisely because no unverified email account is ever allowed to exist. Enforced by leaving the Supabase dashboard's link-same-email default on; no app code.

5. **No auto-select; sign-out stays method-agnostic (Q6/Q9).** `GetGoogleIdOption` runs with auto-select **off**, so "Continue with Google" always shows the account picker (explicit action) — never a silent auto-login. That removes any need for `clearCredentialState()` plumbing on sign-out; the existing sign-out (clear session + `LocalDataWiper`) already keys off session teardown and needs no Google branch.

6. **Cancel is silent; failures are typed (Q6).** `GetCredentialCancellationException` (dismissed sheet) shows **no** error — a cancel is not a failure. `NoCredentialException` (no Google account on device) → a specific message. Any other Credential Manager / ID-token-rejection failure → one generic "couldn't sign in" message (reusing the `NETWORK` copy on connectivity failures). New `AuthError` values (`GOOGLE_NO_ACCOUNT`, `GOOGLE_SIGN_IN_FAILED`) + an independent `isGoogleSubmitting` flag so the Google button spins without touching the email form's button.

7. **Both auth modes, one button (Q5).** Google sign-in is method-agnostic — the same tap creates, signs in, or links. So the outlined "Continue with Google" button (official Google "G" mark) renders in **both** sign-in and sign-up modes, below the primary button after an "or" divider; hiding it in one mode would be a pointless inconsistency.

8. **Config carried per-flavor, staging-only for now (Q7).** The Google **Web** OAuth client ID (server client ID — *not* the Android client ID, the standard failure trap) is stored like the Supabase keys: gitignored `local.properties` → `buildConfigField`. Prod Supabase doesn't exist yet, so staging only. External prerequisites (dead until done): a Google Cloud project + consent screen; a Web OAuth client (ID → app + Supabase dashboard); Android OAuth client(s) registered with **three** SHA-1s — local debug, upload key, and **Play App Signing** key; and the Supabase dashboard Google provider enabled.

## Consequences

Google Sign-In is a thin front-end onto the existing session machinery: nothing in sync (ADR-0002/0009), the users-row bootstrap (ADR-0013), onboarding (ADR-0024), or account-switch purge (ADR-0021) learns it exists. Email verification (previously required before first login) simply doesn't apply — a Google identity is pre-verified, so `signInWith(IDToken)` yields a session directly with no `EMAIL_NOT_CONFIRMED` path. The flow is Android/Credential-Manager-heavy, so most of it is verify-by-running per the testing policy; the JVM-testable seams are the Q3 display-name fallback resolution and the Q6 exception→`AuthError` mapping.

**In-app linking is explicitly out of this decision.** Connecting Google to an *already-signed-in* account (Settings, no sign-out) needs Supabase `linkIdentity(Google)`, which runs the **OAuth redirect (browser)** flow — supabase-kt has no native-ID-token linking path. That pulls back in the very mechanism rejected in decision 1, plus a Settings surface, so it is booked as a **separate follow-up item** (v1.7.0 Item 13), not folded here. Until it ships, an email user can still link implicitly: sign out, "Continue with Google" with the matching Gmail, auto-link (decision 4) — matching email only.

## Rejected alternatives (summary)

- **OAuth redirect / Custom Tab for login** (`signInWith(Google)`) — reuses existing deep-link plumbing but imposes a browser hop on the primary SSO path; native is the current Google/Supabase recommendation.
- **`compose-auth`'s `rememberSignInWithGoogle`** — convenient, but relocates the auth call into the Composable, breaking the ViewModel→UseCase→Repository layering every other auth flow uses.
- **Legacy Google Sign-In SDK** (`GoogleSignInClient`) — deprecated by Google; superseded by Credential Manager.
- **Write-time `display_name` normalize** — extra network round-trip + failure surface versus a read-time fallback.
- **Keeping Google logins as separate accounts** — a "where did my data go?" trap; auto-link by verified email is safe here because unverified accounts can't exist.
- **Folding in-app linking into Item 2** — mixes native + redirect mechanisms and a Settings surface into one item; split to Item 13.
