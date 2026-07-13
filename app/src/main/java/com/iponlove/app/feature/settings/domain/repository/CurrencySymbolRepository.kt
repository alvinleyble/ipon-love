package com.iponlove.app.feature.settings.domain.repository

import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import kotlinx.coroutines.flow.Flow

interface CurrencySymbolRepository {
    fun observe(): Flow<CurrencySymbol>
    suspend fun setSymbol(symbol: CurrencySymbol)
}
