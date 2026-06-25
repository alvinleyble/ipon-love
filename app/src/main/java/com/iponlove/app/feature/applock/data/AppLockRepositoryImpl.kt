package com.iponlove.app.feature.applock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iponlove.app.feature.applock.di.AppLockDataStore
import com.iponlove.app.feature.applock.domain.model.AppLockPreferences
import com.iponlove.app.feature.applock.domain.repository.AppLockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject

class AppLockRepositoryImpl @Inject constructor(
    @AppLockDataStore private val dataStore: DataStore<Preferences>,
) : AppLockRepository {

    override fun observe(): Flow<AppLockPreferences> = dataStore.data.map { prefs ->
        AppLockPreferences(
            isPinSet = prefs[KEY_PIN_HASH] != null,
            isBiometricEnabled = prefs[KEY_BIOMETRIC] ?: false,
        )
    }

    override suspend fun setPin(rawPin: String) {
        val salt = generateSalt()
        val hash = hashPin(rawPin, salt)
        dataStore.edit {
            it[KEY_SALT] = salt
            it[KEY_PIN_HASH] = hash
        }
    }

    override suspend fun verifyPin(rawPin: String): Boolean {
        val prefs = dataStore.data.first()
        val salt = prefs[KEY_SALT] ?: return false
        val stored = prefs[KEY_PIN_HASH] ?: return false
        return hashPin(rawPin, salt) == stored
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC] = enabled }
    }

    override suspend fun clearPin() {
        dataStore.edit {
            it.remove(KEY_PIN_HASH)
            it.remove(KEY_SALT)
            it[KEY_BIOMETRIC] = false
        }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun hashPin(pin: String, salt: String): String {
        val input = (salt + pin).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return Base64.getEncoder().encodeToString(digest)
    }

    companion object {
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_SALT = stringPreferencesKey("pin_salt")
        private val KEY_BIOMETRIC = booleanPreferencesKey("biometric_enabled")
    }
}
