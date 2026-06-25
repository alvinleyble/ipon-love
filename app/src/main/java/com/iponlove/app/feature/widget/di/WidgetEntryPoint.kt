package com.iponlove.app.feature.widget.di

import com.iponlove.app.feature.widget.domain.usecase.GetWidgetDataUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun getWidgetDataUseCase(): GetWidgetDataUseCase
}
