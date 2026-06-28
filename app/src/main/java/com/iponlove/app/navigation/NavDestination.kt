package com.iponlove.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One navigable top-level module. [id] is the stable key persisted in the nav config
 * (DataStore) — never change it once shipped; [route] is the NavHost route. [requiresPaired]
 * destinations only show in the bar/picker while the user is paired (ADR-0017 "hide +
 * graceful collapse"), but their pin stays in the config so re-pairing restores the layout.
 *
 * Note: Couple itself is NOT paired-only — it doubles as the pairing entry point and renders
 * its own create/join UI when unpaired, so hiding it would orphan onboarding. Only Combined
 * and Partner Debt (which read purged partner data when unpaired) are gated.
 *
 * [pinnable] = false means the module can never sit on the bottom bar — it lives permanently in
 * the More sheet so it stays reachable without ever consuming a precious pin slot (ADR-0017).
 * Settings is the canonical non-pinnable module.
 */
data class NavDestination(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val route: String,
    val requiresPaired: Boolean = false,
    val pinnable: Boolean = true,
)

/**
 * The single source of truth for navigable modules. Adding a future module is a one-line
 * entry here — the bottom bar, the More grid, and the editor all pick it up (ADR-0017).
 */
object NavRegistry {
    /** Hard cap on user-pinned destinations; the bar always reserves a slot for "More". */
    const val MAX_PINS = 4

    val RECORDS = NavDestination("records", "Records", Icons.Filled.Receipt, "records")
    val ANALYSIS = NavDestination("analysis", "Analysis", Icons.Filled.PieChart, "analysis")
    // Manage hosts the Accounts / Categories / Budgets tabs (V1.4 IA consolidation — ADR-0017).
    val MANAGE = NavDestination("manage", "Manage", Icons.Filled.Dashboard, "manage")
    val NOTES = NavDestination("notes", "Notes", Icons.Filled.Description, "notes")
    val RECURRING = NavDestination("recurring", "Recurring", Icons.Filled.Repeat, "recurring")
    val COUPLE = NavDestination("couple", "Couple", Icons.Filled.Favorite, "couple")
    val COMBINED = NavDestination("combined", "Combined", Icons.Filled.People, "combined", requiresPaired = true)
    val PARTNER_DEBT = NavDestination("partner_debt", "Debts", Icons.Filled.Handshake, "partner_debt", requiresPaired = true)
    val SETTINGS = NavDestination("settings", "Settings", Icons.Filled.Settings, "settings", pinnable = false)

    /** Registry order — drives the editor's "available" list and the More grid. */
    val all: List<NavDestination> = listOf(
        RECORDS, ANALYSIS, MANAGE,
        NOTES, RECURRING, COUPLE, COMBINED, PARTNER_DEBT, SETTINGS,
    )

    val byId: Map<String, NavDestination> = all.associateBy { it.id }

    /** Ids that only render while paired — kept as a set for the pure [NavResolver]. */
    val pairedOnlyIds: Set<String> = all.filter { it.requiresPaired }.map { it.id }.toSet()

    /** Factory defaults for a fresh install (ADR-0017). */
    val DEFAULT_PINS: List<String> = listOf(RECORDS.id, ANALYSIS.id, COUPLE.id, MANAGE.id)
}
