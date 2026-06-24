package com.iponlove.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom-navigation tabs. The full spec is Records | Analysis | Budgets | Accounts |
 * Categories (ARCHITECTURE §7); only the built features appear here and the bar grows
 * as more land. Records is the home tab. Icons are placeholders until the design pass.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    RECORDS("records", "Records", Icons.Filled.Home),
    BUDGETS("budgets", "Budgets", Icons.Filled.DateRange),
    ACCOUNTS("accounts", "Accounts", Icons.Filled.List),
    CATEGORIES("categories", "Categories", Icons.Filled.ShoppingCart),
}
