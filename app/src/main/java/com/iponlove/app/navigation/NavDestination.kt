package com.iponlove.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One navigable top-level module. [id] is the stable key persisted in the nav config
 * (DataStore) — never change it once shipped; [route] is the NavHost route. [requiresPaired]
 * destinations only show in the bar/picker while the user is paired (ADR-0017 "hide +
 * graceful collapse"), but their pin stays in the config so re-pairing restores the layout.
 *
 * Note: Couple is paired-only (ADR-0026). When unpaired it auto-hides, and its [understudyId]
 * (Manage) is promoted into the freed slot so the bar keeps its item count instead of shrinking:
 * paired bar = Analysis · Records · ⊕ · Couple · More; unpaired = Analysis · Records · ⊕ · Manage
 * · More. Pairing for unpaired users lives in Settings → Couple plus the dismissible Analysis-home
 * pairing card (Slice 3), not on the bar.
 *
 * [pinnable] = false means the module can never sit on the bottom bar — it lives permanently in
 * the More sheet so it stays reachable without ever consuming a precious pin slot (ADR-0017).
 *
 * [understudyId] names a module that stands in for this one on the bar whenever this pin is
 * hidden (currently only the paired-only case). The [NavResolver] promotes it into the vacated
 * slot unless it's already pinned/visible. Purely a bar-rendering concept — the saved config is
 * untouched, so re-pairing restores the original pin.
 */
data class NavDestination(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val route: String,
    val requiresPaired: Boolean = false,
    val pinnable: Boolean = true,
    val understudyId: String? = null,
)

/**
 * The single source of truth for navigable modules. Adding a future module is a one-line
 * entry here — the bottom bar, the More grid, and the editor all pick it up (ADR-0017).
 */
object NavRegistry {
    /**
     * Hard cap on user-pinned destinations. With the fixed center ⊕ Add and the fixed More slot,
     * 3 pins keep the bar at the five-item Material ceiling (3 pins + ⊕ + More); 4 would overflow
     * to six on a phone bottom bar (ADR-0026).
     */
    const val MAX_PINS = 3

    val RECORDS = NavDestination("records", "Records", Icons.Filled.Receipt, "records")
    val ANALYSIS = NavDestination("analysis", "Analysis", Icons.Filled.PieChart, "analysis")
    // Manage hosts the Accounts / Categories / Budgets tabs (V1.4 IA consolidation — ADR-0017).
    val MANAGE = NavDestination("manage", "Manage", Icons.Filled.Dashboard, "manage")
    val NOTES = NavDestination("notes", "Notes", Icons.Filled.Description, "notes")
    val RECURRING = NavDestination("recurring", "Recurring", Icons.Filled.Repeat, "recurring")
    val COUPLE = NavDestination("couple", "Couple", Icons.Filled.Favorite, "couple", requiresPaired = true, understudyId = MANAGE.id)
    val COMBINED = NavDestination("combined", "Combined", Icons.Filled.People, "combined", requiresPaired = true)
    val PARTNER_DEBT = NavDestination("partner_debt", "Debts", Icons.Filled.Handshake, "partner_debt", requiresPaired = true)
    val SETTINGS = NavDestination("settings", "Settings", Icons.Filled.Settings, "settings")
    val CALCULATOR = NavDestination("calculator", "Calculator", Icons.Filled.Calculate, "calculator")
    // Shared savings goals — own pinnable module, in More by default (ADR-0025), not a Manage tab.
    val SAVINGS = NavDestination("savings", "Savings", Icons.Filled.Savings, "savings")

    /** Registry order — drives the editor's "available" list and the More grid. */
    val all: List<NavDestination> = listOf(
        RECORDS, ANALYSIS, MANAGE, COUPLE, SETTINGS, CALCULATOR, SAVINGS,
    )

    val byId: Map<String, NavDestination> = all.associateBy { it.id }

    /** Ids that only render while paired — kept as a set for the pure [NavResolver]. */
    val pairedOnlyIds: Set<String> = all.filter { it.requiresPaired }.map { it.id }.toSet()

    /**
     * Factory defaults for a fresh install — analysis-first so Analysis is home (ADR-0026).
     * Paired bar: Analysis · Records · ⊕ · Couple · More; unpaired: Analysis · Records · ⊕ ·
     * Manage · More (Couple hidden, its understudy Manage promoted — see [NavResolver]).
     */
    val DEFAULT_PINS: List<String> = listOf(ANALYSIS.id, RECORDS.id, COUPLE.id)
}
