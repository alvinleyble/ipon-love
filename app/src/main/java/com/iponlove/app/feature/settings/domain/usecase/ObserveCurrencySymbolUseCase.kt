package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.settings.domain.repository.CurrencySymbolRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCurrencySymbolUseCase @Inject constructor(
    private val repository: CurrencySymbolRepository,
) {
    operator fun invoke(): Flow<CurrencySymbol> = repository.observe()
}
