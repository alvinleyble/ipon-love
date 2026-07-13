package com.iponlove.app.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iponlove.app.feature.settings.di.CurrencyDataStore
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.settings.domain.repository.CurrencySymbolRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CurrencySymbolRepositoryImpl @Inject constructor(
    @CurrencyDataStore private val dataStore: DataStore<Preferences>,
) : CurrencySymbolRepository {

    override fun observe(): Flow<CurrencySymbol> =
        dataStore.data.map { prefs ->
            // Tolerate an unknown/removed enum name (e.g. a symbol dropped in a later build) by
            // falling back to the default rather than crashing the whole preferences flow.
            prefs[KEY_SYMBOL]
                ?.let { stored -> runCatching { CurrencySymbol.valueOf(stored) }.getOrNull() }
                ?: CurrencySymbol.DEFAULT
        }

    override suspend fun setSymbol(symbol: CurrencySymbol) {
        dataStore.edit { p -> p[KEY_SYMBOL] = symbol.name }
    }

    private companion object {
        val KEY_SYMBOL = stringPreferencesKey("currency_symbol")
    }
}
