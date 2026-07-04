package com.iponlove.app.feature.applock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iponlove.app.feature.applock.di.AppLockDataStore
import com.iponlove.app.feature.applock.domain.model.AppLockPreferences
import com.iponlove.app.feature.applock.domain.repository.AppLockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.inject.Inject

class AppLockRepositoryImpl @Inject constructor(
    @AppLockDataStore private val dataStore: DataStore<Preferences>,
) : AppLockRepository {

    override fun observe(): Flow<AppLockPreferences> = dataStore.data.map { prefs ->
        AppLockPreferences(
            isPinSet = prefs[KEY_PIN_HASH] != null,
            isBiometricEnabled = prefs[KEY_BIOMETRIC] ?: false,
            biometricNudgeShown = prefs[KEY_NUDGE_SHOWN] ?: false,
            failedPinAttempts = prefs[KEY_FAILED_ATTEMPTS] ?: 0,
            pinLockoutUntil = prefs[KEY_LOCKOUT_UNTIL]?.let(Instant::ofEpochSecond),
        )
    }

    override suspend fun setPin(rawPin: String) {
        val salt = generateSalt()
        val hash = hashPin(rawPin, salt)
        dataStore.edit {
            it[KEY_SALT] = salt
            it[KEY_PIN_HASH] = hash
            it.remove(KEY_FAILED_ATTEMPTS)
            it.remove(KEY_LOCKOUT_UNTIL)
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

    override suspend fun markBiometricNudgeShown() {
        dataStore.edit { it[KEY_NUDGE_SHOWN] = true }
    }

    override suspend fun clearPin() {
        dataStore.edit {
            it.remove(KEY_PIN_HASH)
            it.remove(KEY_SALT)
            it[KEY_BIOMETRIC] = false
            it.remove(KEY_NUDGE_SHOWN)
            it.remove(KEY_FAILED_ATTEMPTS)
            it.remove(KEY_LOCKOUT_UNTIL)
        }
    }

    override suspend fun setPinAttemptState(failedAttempts: Int, lockoutUntil: Instant?) {
        dataStore.edit {
            if (failedAttempts == 0) it.remove(KEY_FAILED_ATTEMPTS) else it[KEY_FAILED_ATTEMPTS] = failedAttempts
            if (lockoutUntil == null) it.remove(KEY_LOCKOUT_UNTIL) else it[KEY_LOCKOUT_UNTIL] = lockoutUntil.epochSecond
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
        private val KEY_NUDGE_SHOWN = booleanPreferencesKey("biometric_nudge_shown")
        private val KEY_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        private val KEY_LOCKOUT_UNTIL = longPreferencesKey("pin_lockout_until_epoch_sec")
    }
}
