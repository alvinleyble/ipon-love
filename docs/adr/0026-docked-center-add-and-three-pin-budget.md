# Docked center Add button and a 3-pin budget (amends ADR-0017)

The customizable navbar (ADR-0017) gains a permanent, **non-editable center ⊕ Add** action that navigates to the full-screen add-transaction route, and `MAX_PINS` drops from **4 to 3**.

With a fixed center ⊕ and a fixed More slot, three user pins keep the bar at the five-item Material ceiling (`3 pins + ⊕ + More`). Leaving `MAX_PINS` at 4 would let a user pin enough to overflow to six items on a phone bottom bar. The center slot is reserved — the navbar editor manages the side pins only — and is rendered as an **accented item**, not a cradle FAB, to get visual emphasis for the app's highest-frequency action without swapping `NavigationBar` for `BottomAppBar` and reworking insets.

## Consequences

- Default visible bar: **Analysis · Records · ⊕ · Couple · More**, with **Analysis as home** (`DEFAULT_PINS` reordered analysis-first; `Settings` flipped to `pinnable=true`).
- `COUPLE` becomes `requiresPaired=true`, so `NavResolver.visiblePinIds` auto-hides it when unpaired (existing infra) and `startRoute` never picks it as home. Unpaired bar gracefully becomes `Analysis · Records · ⊕ · More`. Pairing/unpair relocates to **Settings → Couple**, plus a dismissible pairing card on the Analysis home for unpaired users so the activation event isn't buried.
- A beta user with 4 pins saved in DataStore has the 4th silently dropped (render first 3).
