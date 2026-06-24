package com.iponlove.app.core.di

import com.iponlove.app.core.network.createIponSupabaseClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

/** Provides the single app-wide [SupabaseClient] (Auth + Postgrest installed). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun supabaseClient(): SupabaseClient = createIponSupabaseClient()
}
