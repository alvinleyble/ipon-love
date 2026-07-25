> Archived 2026-07-25 — superseded by `docs/build/v1.6.1.md`. Kept for historical reference only.

Fixed beta feedback screen showing misleading "(prod)" version tag
Records & Combined view now load month-by-month with sticky day headers instead of your entire history at once (much faster at scale)
Added forgot password flow, including a "set new password" screen and fixes for several email/deep-link bugs
Fixed navbar editor bugs where "Couple" could vanish or disagree with the actual bottom bar; unpaired users now default to Analysis/Records/Manage
New Settings → Beta section with Beta Feedback + an "Upcoming Features" roadmap page
Fixed biometric app lock not prompting (was silently failing) — biometric now auto-prompts first, PIN is backup only
Added PIN lockout after 5 wrong attempts (30s cooldown)
Forced app update prompt for beta testers on version mismatch
Added an explanatory caption to the "Private" transaction toggle
Redesigned Analysis time filter with more presets: Day/Week/Month/Quarter/Half-year/Year/All-time
Added optional transfer fee tracking on transfer transactions (recorded as a linked expense)
Notes promoted to its own top-level nav tab (previously hidden behind an icon in Records)
Fixed tab navigation: switching tabs now preserves where you left off in each module; re-tapping the current tab resets it to its root
Added a replayable first-run tutorial (Settings → Support → "Replay tutorial")
Added Privacy Policy link in Settings
Recurring transactions can now be paused, resumed, or have their next occurrence skipped; added "Annually" frequency; recurring editor's account/category pickers now match the main transaction screen
