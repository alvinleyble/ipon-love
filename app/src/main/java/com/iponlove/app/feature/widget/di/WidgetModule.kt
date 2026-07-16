package com.iponlove.app.feature.widget.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.iponlove.app.feature.widget.data.WidgetSessionStore
import com.iponlove.app.feature.widget.presentation.WidgetRefresher
import com.iponlove.app.feature.widget.presentation.Widgets
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Widget-only preferences: the cached "has session?" hint the balance widget reads (Item 36). */
private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_prefs")

@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {

    @Provides
    @Singleton
    fun provideWidgetRefresher(@ApplicationContext context: Context): WidgetRefresher =
        WidgetRefresher { Widgets.updateAll(context) }

    @Provides
    @Singleton
    fun provideWidgetSessionStore(@ApplicationContext context: Context): WidgetSessionStore =
        WidgetSessionStore(context.widgetDataStore)
}
