# Theme / Personalize design

**Context.** V1 requires multiple color themes. The design needed to resolve: how many themes, what model (named combos vs palette × mode), what palettes, how they relate to the couple attribution color, and where/how the user picks them.

**Decisions.**

**Palette × Mode model.** A user picks a *palette* and separately toggles *light/dark mode*. These are independent axes stored separately (palette in DataStore, dark mode in DataStore). 6 palettes × 2 modes = 12 visual combinations from two simple controls. Adding a new palette post-V1 requires one new object, no structural changes.

**Six palettes, all within a soft/romantic tone:**

| Palette | Light primary seed |
|---|---|
| Rose | `#C2647A` |
| Mauve | `#9B6B7A` |
| Lavender | `#8B7BB5` |
| Peach | `#C47A5A` |
| Sage | `#6B8F71` |
| Mocha | `#8B6F5A` |

Material 3 derives the full `ColorScheme` (all roles, light + dark tones) from the single seed via tonal palette generation. The dark theme for each palette uses M3's standard dark-mode tones — lighter pastel primaries on warm-dark surfaces — without manual specification.

**Personalize screen (Settings → Personalize).** A dedicated screen with a `LazyVerticalGrid` of 6 palette swatches + a light/dark toggle. Tapping a swatch applies it live to the Personalize screen itself (held in local ViewModel state, not yet persisted) so the user sees real UI recoloring before committing. A Save/Apply button persists to DataStore. `IponTheme` reads from DataStore at the app root via `CompositionLocal`, so the whole app recolors on next composition after save.

**Theme palette is personal appearance only; it is not the couple attribution color.** The two are independent. The palette affects how the app looks to the individual user. Couple attribution (whose transaction is whose in the combined view) uses a fixed blue vs pink pair that never changes based on theme.

**Combined view attribution: fixed blue vs pink.** Both partners always see the same two colors in the combined view regardless of their personal palette choice. This avoids the edge case where both partners pick the same theme and attribution becomes indistinguishable. The specific color (blue or pink) is chosen by each partner during the couple pairing flow (not at initial profile setup, where no partner exists yet). The chosen color is stored as `accent_color` on the `users` row and synced.

**Rejected: theme-as-accent-color.** Using the palette choice as the couple attribution color was considered (simpler, one choice instead of two) but rejected because it breaks when partners choose the same palette.

**Rejected: block duplicate palette selection.** Preventing a partner from choosing the same palette as the other was considered but rejected — attribution and palette are now decoupled so the collision no longer matters.
