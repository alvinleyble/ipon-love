package com.iponlove.app.feature.onboarding.domain.model

/**
 * An à-la-carte group of starter categories/accounts offered on the onboarding template
 * picker (ADR-0024). All entries are pre-checked by default; unchecking one omits it from
 * [com.iponlove.app.feature.onboarding.domain.usecase.SeedStarterDataUseCase].
 */
enum class StarterBundle(val label: String, val description: String) {
    EVERYDAY_SPENDING("Everyday spending", "Food, groceries, transport, shopping"),
    BILLS_UTILITIES("Bills & utilities", "Rent, electricity, water, internet, phone load"),
    INCOME("Income", "Salary, business, gifts"),
    ACCOUNTS("Accounts", "Cash, GCash, bank account"),
}
