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
 * (DataStore) — never change it once shipped; [route] is the NavHost route.
 *
 * [requiresPaired] is *informational metadata only* (2026-07-04 redesign): it drives the
 * "Paired only" caption in the navbar editor, nothing else. It no longer hides the module from
 * the bar, the More sheet, or the editor — a pinned module always renders, and Couple opens its
 * own pairing (create/join) page when unpaired. Pairing state never rewrites the bar; the only
 * config change tied to pairing is the explicit pin-on-create/join in the couple flow.
 *
 * [pinnable] = false means the module can never sit on the bottom bar — it lives permanently in
 * the More sheet so it stays reachable without ever consuming a precious pin slot (ADR-0017).
 *
 * [navigable] = false marks an **overlay module** (ADR-0058): it keeps its registry entry — so it
 * stays pinnable, listed in the More sheet, and orderable in the navbar editor — but it owns no
 * nav graph at all. Tapping it acts on the *current* screen (Calculator spawns its bubble) instead
 * of navigating, so its [route] is never handed to the [NavHost][androidx.navigation.NavHost].
 * Anything that turns a module id into a destination must consult this, not mere registry
 * membership: [NavResolver.startRoute] and [NavRestorePolicy] both would otherwise start the
 * NavHost on a route that doesn't exist.
 */
data class NavDestination(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val route: String,
    val requiresPaired: Boolean = false,
    val pinnable: Boolean = true,
    val navigable: Boolean = true,
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
    val COUPLE = NavDestination("couple", "Couple", Icons.Filled.Favorite, "couple", requiresPaired = true)
    val COMBINED = NavDestination("combined", "Spending", Icons.Filled.People, "combined", requiresPaired = true)
    val PARTNER_DEBT = NavDestination("partner_debt", "Debts", Icons.Filled.Handshake, "partner_debt", requiresPaired = true)
    val SETTINGS = NavDestination("settings", "Settings", Icons.Filled.Settings, "settings")
    // Calculator is the registry's only *overlay* module (ADR-0058): tapping it spawns the
    // floating bubble over whatever screen you're on, so it has no nav graph and its route is
    // never navigated to. It stays a first-class registry entry so it keeps its pin, its More
    // sheet cell, and its navbar-editor row.
    val CALCULATOR =
        NavDestination("calculator", "Calculator", Icons.Filled.Calculate, "calculator", navigable = false)
    // Shared savings goals — own pinnable module, in More by default (ADR-0025), not a Manage tab.
    val SAVINGS = NavDestination("savings", "Savings", Icons.Filled.Savings, "savings")

    /** Registry order — drives the editor's "available" list and the More grid. */
    val all: List<NavDestination> = listOf(
        RECORDS, ANALYSIS, MANAGE, COUPLE, SETTINGS, CALCULATOR, SAVINGS, NOTES, RECURRING,
    )

    val byId: Map<String, NavDestination> = all.associateBy { it.id }

    /**
     * Factory defaults for a fresh install — analysis-first so Analysis is home (ADR-0026).
     * Exactly [MAX_PINS] ids. Solo users start without Couple pinned (bar: Analysis · Records ·
     * ⊕ · Manage · More); creating or joining a couple explicitly pins Couple in Manage's place
     * (see [NavConfig.ensurePinned] + the couple flow). Couple stays reachable from the More
     * sheet regardless.
     */
    val DEFAULT_PINS: List<String> = listOf(ANALYSIS.id, RECORDS.id, MANAGE.id)
}
