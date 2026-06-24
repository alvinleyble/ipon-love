package com.iponlove.app.feature.categories.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.categories.data.CategoryRepositoryImpl
import com.iponlove.app.feature.categories.data.remote.CategoryRemoteSource
import com.iponlove.app.feature.categories.data.remote.SupabaseCategoryRemoteSource
import com.iponlove.app.feature.categories.data.sync.CategoryTableSyncer
import com.iponlove.app.feature.categories.data.sync.PartnerCategoryTableSyncer
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface CategoryModule {

    @Binds
    fun categoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    // No-op stub until the Supabase backend slice provides the real remote source.
    @Binds
    @Singleton
    fun categoryRemoteSource(impl: SupabaseCategoryRemoteSource): CategoryRemoteSource

    /** Contributes categories to the sync engine's table set; engine sorts into FK order. */
    @Binds
    @IntoSet
    fun categoryTableSyncer(impl: CategoryTableSyncer): TableSyncer

    /** Contributes the partner-categories replica pull (ADR-0004/0005). */
    @Binds
    @IntoSet
    fun partnerCategoryTableSyncer(impl: PartnerCategoryTableSyncer): TableSyncer
}
