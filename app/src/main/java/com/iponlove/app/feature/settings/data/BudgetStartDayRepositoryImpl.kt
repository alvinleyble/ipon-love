package com.iponlove.app.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.iponlove.app.feature.budgets.domain.usecase.BudgetCycle
import com.iponlove.app.feature.settings.di.FinanceDataStore
import com.iponlove.app.feature.settings.domain.repository.BudgetStartDayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BudgetStartDayRepositoryImpl @Inject constructor(
    @FinanceDataStore private val dataStore: DataStore<Preferences>,
) : BudgetStartDayRepository {

    override fun observe(): Flow<Int> =
        dataStore.data.map { prefs ->
            // Clamp defensively so a corrupt/out-of-range stored value can never break cycle math.
            (prefs[KEY_START_DAY] ?: BudgetCycle.MIN_START_DAY)
                .coerceIn(BudgetCycle.MIN_START_DAY, BudgetCycle.MAX_START_DAY)
        }

    override suspend fun setStartDay(day: Int) {
        val clamped = day.coerceIn(BudgetCycle.MIN_START_DAY, BudgetCycle.MAX_START_DAY)
        dataStore.edit { p -> p[KEY_START_DAY] = clamped }
    }

    private companion object {
        val KEY_START_DAY = intPreferencesKey("budget_start_day")
    }
}
