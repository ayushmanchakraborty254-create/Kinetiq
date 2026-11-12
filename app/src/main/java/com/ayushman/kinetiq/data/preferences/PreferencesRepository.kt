package com.ayushman.kinetiq.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ayushman.kinetiq.domain.model.SensitivityLevel
import com.ayushman.kinetiq.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kinetiq_settings")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val CHOP_ENABLED = booleanPreferencesKey("chop_enabled")
        val TWIST_ENABLED = booleanPreferencesKey("twist_enabled")
        val CHOP_SENSITIVITY = stringPreferencesKey("chop_sensitivity")
        val TWIST_SENSITIVITY = stringPreferencesKey("twist_sensitivity")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val POCKET_PROTECTION_ENABLED = booleanPreferencesKey("pocket_protection_enabled")
        val SCREEN_OFF_MONITORING = booleanPreferencesKey("screen_off_monitoring")
        val CUSTOM_CHOP_THRESHOLD = floatPreferencesKey("custom_chop_threshold")
        val CUSTOM_TWIST_THRESHOLD = floatPreferencesKey("custom_twist_threshold")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                isMonitoringEnabled = preferences[Keys.MONITORING_ENABLED] ?: false,
                isChopEnabled = preferences[Keys.CHOP_ENABLED] ?: true,
                isTwistEnabled = preferences[Keys.TWIST_ENABLED] ?: true,
                chopSensitivity = preferences[Keys.CHOP_SENSITIVITY]?.let {
                    runCatching { SensitivityLevel.valueOf(it) }.getOrNull()
                } ?: SensitivityLevel.MEDIUM,
                twistSensitivity = preferences[Keys.TWIST_SENSITIVITY]?.let {
                    runCatching { SensitivityLevel.valueOf(it) }.getOrNull()
                } ?: SensitivityLevel.MEDIUM,
                isHapticEnabled = preferences[Keys.HAPTIC_ENABLED] ?: true,
                isPocketProtectionEnabled = preferences[Keys.POCKET_PROTECTION_ENABLED] ?: true,
                isScreenOffMonitoringEnabled = preferences[Keys.SCREEN_OFF_MONITORING] ?: false,
                customChopPeakThreshold = preferences[Keys.CUSTOM_CHOP_THRESHOLD],
                customTwistPeakThreshold = preferences[Keys.CUSTOM_TWIST_THRESHOLD]
            )
        }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITORING_ENABLED] = enabled }
    }

    suspend fun setChopEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CHOP_ENABLED] = enabled }
    }

    suspend fun setTwistEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TWIST_ENABLED] = enabled }
    }

    suspend fun setChopSensitivity(sensitivity: SensitivityLevel) {
        context.dataStore.edit { it[Keys.CHOP_SENSITIVITY] = sensitivity.name }
    }

    suspend fun setTwistSensitivity(sensitivity: SensitivityLevel) {
        context.dataStore.edit { it[Keys.TWIST_SENSITIVITY] = sensitivity.name }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun setPocketProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.POCKET_PROTECTION_ENABLED] = enabled }
    }

    suspend fun setScreenOffMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCREEN_OFF_MONITORING] = enabled }
    }

    suspend fun setCustomChopThreshold(threshold: Float?) {
        context.dataStore.edit {
            if (threshold != null) {
                it[Keys.CUSTOM_CHOP_THRESHOLD] = threshold
            } else {
                it.remove(Keys.CUSTOM_CHOP_THRESHOLD)
            }
        }
    }

    suspend fun setCustomTwistThreshold(threshold: Float?) {
        context.dataStore.edit {
            if (threshold != null) {
                it[Keys.CUSTOM_TWIST_THRESHOLD] = threshold
            } else {
                it.remove(Keys.CUSTOM_TWIST_THRESHOLD)
            }
        }
    }

    suspend fun resetCalibration() {
        context.dataStore.edit {
            it.remove(Keys.CUSTOM_CHOP_THRESHOLD)
            it.remove(Keys.CUSTOM_TWIST_THRESHOLD)
        }
    }
}

