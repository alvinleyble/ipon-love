package com.iponlove.app.feature.onboarding.domain.model

import com.iponlove.app.feature.accounts.domain.model.AccountType
import com.iponlove.app.feature.categories.domain.model.CategoryType

/** One row [SeedStarterDataUseCase][com.iponlove.app.feature.onboarding.domain.usecase.SeedStarterDataUseCase]
 *  can seed. [key] is a stable per-item id fragment for the deterministic row id — never rename
 *  once shipped, or a reseed would treat the row as new instead of idempotently overwriting it. */
data class StarterCategoryItem(
    val key: String,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val color: String,
    /** Pass-through category (ADR-0049): its transactions are excluded from Analysis/Budgets/Combined. */
    val excludeFromAnalysis: Boolean = false,
)

data class StarterAccountItem(
    val key: String,
    val name: String,
    val type: AccountType,
    val icon: String,
    val color: String,
)

/** PH-market starter content for the four onboarding bundles (ADR-0024). */
object StarterCatalog {

    val EVERYDAY_SPENDING = listOf(
        StarterCategoryItem("food", "Food & Dining", CategoryType.EXPENSE, "food", "#F4511E"),
        StarterCategoryItem("groceries", "Groceries", CategoryType.EXPENSE, "groceries", "#7CB342"),
        StarterCategoryItem("transport", "Transportation", CategoryType.EXPENSE, "transport", "#1E88E5"),
        StarterCategoryItem("shopping", "Shopping", CategoryType.EXPENSE, "shopping", "#D81B60"),
    )

    val BILLS_UTILITIES = listOf(
        StarterCategoryItem("rent", "Rent", CategoryType.EXPENSE, "rent", "#6D4C41"),
        StarterCategoryItem("electricity", "Electricity", CategoryType.EXPENSE, "utilities", "#F4B400"),
        StarterCategoryItem("water", "Water", CategoryType.EXPENSE, "water", "#039BE5"),
        StarterCategoryItem("internet", "Internet", CategoryType.EXPENSE, "wifi", "#3949AB"),
        StarterCategoryItem("phoneload", "Phone Load", CategoryType.EXPENSE, "phone", "#546E7A"),
    )

    val INCOME = listOf(
        StarterCategoryItem("salary", "Salary", CategoryType.INCOME, "salary", "#43A047"),
        StarterCategoryItem("business", "Business", CategoryType.INCOME, "business", "#00897B"),
        StarterCategoryItem("gift", "Gifts", CategoryType.INCOME, "gift", "#8E24AA"),
    )

    // Pass-through pair (ADR-0049): both flagged excludeFromAnalysis so a work expense you'll be
    // repaid — and the repayment — stay out of Analysis/Budgets/Combined while still moving balance.
    val REIMBURSABLES = listOf(
        StarterCategoryItem("reimbursable", "Reimbursable", CategoryType.EXPENSE, "work", "#5E35B1", excludeFromAnalysis = true),
        StarterCategoryItem("reimbursement", "Reimbursement", CategoryType.INCOME, "payment", "#00ACC1", excludeFromAnalysis = true),
    )

    val ACCOUNTS = listOf(
        StarterAccountItem("cash", "Cash", AccountType.CASH, "cash", "#7CB342"),
        StarterAccountItem("gcash", "GCash", AccountType.EWALLET, "ewallet", "#1E88E5"),
        StarterAccountItem("bank", "Bank Account", AccountType.BANK, "bank", "#3949AB"),
    )
}
