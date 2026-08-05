package com.iponlove.app.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.iponlove.app.feature.settings.di.FinanceDataStore
import com.iponlove.app.feature.settings.domain.repository.ReceiptPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ReceiptPreferencesRepositoryImpl @Inject constructor(
    @FinanceDataStore private val dataStore: DataStore<Preferences>,
) : ReceiptPreferencesRepository {

    // Default ON (ADR-0062 decision 7): the copy is the user's own photo on their own phone.
    override fun observeGalleryCopyEnabled(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_GALLERY_COPY_ENABLED] ?: true }

    override suspend fun setGalleryCopyEnabled(enabled: Boolean) {
        dataStore.edit { p -> p[KEY_GALLERY_COPY_ENABLED] = enabled }
    }

    private companion object {
        val KEY_GALLERY_COPY_ENABLED = booleanPreferencesKey("receipt_gallery_copy_enabled")
    }
}
