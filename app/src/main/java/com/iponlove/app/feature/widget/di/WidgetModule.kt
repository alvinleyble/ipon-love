package com.iponlove.app.feature.widget.di

import android.content.Context
import com.iponlove.app.feature.widget.presentation.WidgetRefresher
import com.iponlove.app.feature.widget.presentation.Widgets
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {

    @Provides
    @Singleton
    fun provideWidgetRefresher(@ApplicationContext context: Context): WidgetRefresher =
        WidgetRefresher { Widgets.updateAll(context) }
}
