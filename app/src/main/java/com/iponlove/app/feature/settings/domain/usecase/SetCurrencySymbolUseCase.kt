package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.settings.domain.repository.CurrencySymbolRepository
import javax.inject.Inject

class SetCurrencySymbolUseCase @Inject constructor(
    private val repository: CurrencySymbolRepository,
) {
    suspend operator fun invoke(symbol: CurrencySymbol) = repository.setSymbol(symbol)
}
