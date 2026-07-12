package com.iponlove.app.core.di

import android.content.Context
import coil.ImageLoader
import com.iponlove.app.core.network.AndroidConnectivityObserver
import com.iponlove.app.core.network.ConnectivityObserver
import com.iponlove.app.core.network.StorageAuthInterceptor
import com.iponlove.app.core.network.createIponSupabaseClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import okhttp3.OkHttpClient
import javax.inject.Singleton

/** Provides the single app-wide [SupabaseClient] (Auth + Postgrest installed). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun supabaseClient(): SupabaseClient = createIponSupabaseClient()

    @Provides
    @Singleton
    fun connectivityObserver(@ApplicationContext context: Context): ConnectivityObserver =
        AndroidConnectivityObserver(context)

    /**
     * App-wide Coil loader ([IponApp][com.iponlove.app.IponApp] returns it from
     * [coil.ImageLoaderFactory]), with [StorageAuthInterceptor] so private-bucket photos load.
     */
    @Provides
    @Singleton
    fun imageLoader(@ApplicationContext context: Context, client: SupabaseClient): ImageLoader =
        ImageLoader.Builder(context)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(StorageAuthInterceptor(client))
                    .build()
            }
            .build()
}
