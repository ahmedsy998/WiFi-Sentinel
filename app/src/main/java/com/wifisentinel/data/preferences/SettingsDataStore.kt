package com.wifisentinel.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wifisentinel.domain.model.AppLanguage
import com.wifisentinel.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.sentinelSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wifi_sentinel_preferences"
)

class SettingsDataStore(
    private val dataStore: DataStore<Preferences>
) {
    val settingsFlow: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppSettings(
                darkTheme = prefs[KEY_DARK_THEME] ?: true,
                language = AppLanguage.fromCode(prefs[KEY_LANGUAGE]),
                maskPasswordsByDefault = prefs[KEY_MASK_PASSWORDS] ?: true,
                biometricProtectionEnabled = prefs[KEY_BIOMETRIC_PROTECTION] ?: false,
                appLockEnabled = prefs[KEY_APP_LOCK] ?: false,
                appLockPinHash = prefs[KEY_APP_LOCK_PIN_HASH],
                notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
                autoClearClipboard = prefs[KEY_AUTO_CLEAR_CLIPBOARD] ?: true,
                blockScreenshots = prefs[KEY_BLOCK_SCREENSHOTS] ?: true
            )
        }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[KEY_DARK_THEME] = enabled }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_LANGUAGE] = language.code }
    }

    suspend fun setMaskPasswordsByDefault(enabled: Boolean) {
        dataStore.edit { it[KEY_MASK_PASSWORDS] = enabled }
    }

    suspend fun setBiometricProtectionEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC_PROTECTION] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_APP_LOCK] = enabled }
    }

    suspend fun setAppLockPinHash(pinHash: String?) {
        dataStore.edit { prefs ->
            if (pinHash == null) {
                prefs.remove(KEY_APP_LOCK_PIN_HASH)
            } else {
                prefs[KEY_APP_LOCK_PIN_HASH] = pinHash
            }
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun setAutoClearClipboard(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_CLEAR_CLIPBOARD] = enabled }
    }

    suspend fun setBlockScreenshots(enabled: Boolean) {
        dataStore.edit { it[KEY_BLOCK_SCREENSHOTS] = enabled }
    }

    companion object {
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        private val KEY_LANGUAGE = stringPreferencesKey("language_code")
        private val KEY_MASK_PASSWORDS = booleanPreferencesKey("mask_passwords_default")
        private val KEY_BIOMETRIC_PROTECTION = booleanPreferencesKey("biometric_protection")
        private val KEY_APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        private val KEY_APP_LOCK_PIN_HASH = stringPreferencesKey("app_lock_pin_hash")
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val KEY_AUTO_CLEAR_CLIPBOARD = booleanPreferencesKey("auto_clear_clipboard")
        private val KEY_BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
    }
}
