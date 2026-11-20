package com.xiaogou.serverseyes.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xiaogou.serverseyes.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "server_eyes_settings")

data class AppSettings(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val pollIntervalSeconds: Int = 5,
    val backgroundWorkEnabled: Boolean = true,
    val foregroundServiceEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val POLL_INTERVAL_SEC = intPreferencesKey("poll_interval_seconds")
        val BACKGROUND_WORK = booleanPreferencesKey("background_work_enabled")
        val FOREGROUND_SERVICE = booleanPreferencesKey("foreground_service_enabled")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val theme = runCatching {
            ThemePreference.valueOf(prefs[Keys.THEME] ?: ThemePreference.SYSTEM.name)
        }.getOrDefault(ThemePreference.SYSTEM)
        AppSettings(
            themePreference = theme,
            pollIntervalSeconds = (prefs[Keys.POLL_INTERVAL_SEC] ?: 5).coerceIn(3, 300),
            backgroundWorkEnabled = prefs[Keys.BACKGROUND_WORK] ?: true,
            foregroundServiceEnabled = prefs[Keys.FOREGROUND_SERVICE] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true
        )
    }

    suspend fun setThemePreference(pref: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = pref.name }
    }

    suspend fun setPollIntervalSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.POLL_INTERVAL_SEC] = seconds.coerceIn(3, 300) }
    }

    suspend fun setBackgroundWorkEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_WORK] = enabled }
    }

    suspend fun setForegroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FOREGROUND_SERVICE] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }
}
