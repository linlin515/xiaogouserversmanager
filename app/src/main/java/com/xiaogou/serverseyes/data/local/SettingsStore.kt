package com.xiaogou.serverseyes.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xiaogou.serverseyes.core.model.ServerSortOption
import com.xiaogou.serverseyes.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "server_eyes_settings")

data class AppSettings(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val pollIntervalSeconds: Int = 5,
    val backgroundWorkEnabled: Boolean = true,
    val backgroundIntervalMinutes: Int = 15,
    val foregroundServiceEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val notifyOnRecovery: Boolean = true,
    val dndEnabled: Boolean = false,
    val dndStartMinute: Int = 23 * 60,
    val dndEndMinute: Int = 8 * 60,
    val dndAllowCritical: Boolean = true,
    val sortOption: ServerSortOption = ServerSortOption.DEFAULT
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val POLL_INTERVAL_SEC = intPreferencesKey("poll_interval_seconds")
        val BACKGROUND_WORK = booleanPreferencesKey("background_work_enabled")
        val BACKGROUND_INTERVAL_MIN = intPreferencesKey("background_interval_minutes")
        val FOREGROUND_SERVICE = booleanPreferencesKey("foreground_service_enabled")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val NOTIFY_RECOVERY = booleanPreferencesKey("notify_on_recovery")
        val DND_ENABLED = booleanPreferencesKey("dnd_enabled")
        val DND_START_MIN = intPreferencesKey("dnd_start_minute")
        val DND_END_MIN = intPreferencesKey("dnd_end_minute")
        val DND_ALLOW_CRITICAL = booleanPreferencesKey("dnd_allow_critical")
        val SORT_OPTION = stringPreferencesKey("dashboard_sort_option")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val theme = runCatching {
            ThemePreference.valueOf(prefs[Keys.THEME] ?: ThemePreference.SYSTEM.name)
        }.getOrDefault(ThemePreference.SYSTEM)
        val sort = runCatching {
            ServerSortOption.valueOf(prefs[Keys.SORT_OPTION] ?: ServerSortOption.DEFAULT.name)
        }.getOrDefault(ServerSortOption.DEFAULT)
        AppSettings(
            themePreference = theme,
            pollIntervalSeconds = (prefs[Keys.POLL_INTERVAL_SEC] ?: 5).coerceIn(3, 300),
            backgroundWorkEnabled = prefs[Keys.BACKGROUND_WORK] ?: true,
            backgroundIntervalMinutes = (prefs[Keys.BACKGROUND_INTERVAL_MIN] ?: 15).coerceIn(15, 720),
            foregroundServiceEnabled = prefs[Keys.FOREGROUND_SERVICE] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            notifyOnRecovery = prefs[Keys.NOTIFY_RECOVERY] ?: true,
            dndEnabled = prefs[Keys.DND_ENABLED] ?: false,
            dndStartMinute = (prefs[Keys.DND_START_MIN] ?: (23 * 60)).coerceIn(0, 1439),
            dndEndMinute = (prefs[Keys.DND_END_MIN] ?: (8 * 60)).coerceIn(0, 1439),
            dndAllowCritical = prefs[Keys.DND_ALLOW_CRITICAL] ?: true,
            sortOption = sort
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

    suspend fun setBackgroundIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.BACKGROUND_INTERVAL_MIN] = minutes.coerceIn(15, 720) }
    }

    suspend fun setForegroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FOREGROUND_SERVICE] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun setNotifyOnRecovery(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_RECOVERY] = enabled }
    }

    suspend fun setDndConfig(
        enabled: Boolean,
        startMinute: Int,
        endMinute: Int,
        allowCritical: Boolean
    ) {
        context.dataStore.edit {
            it[Keys.DND_ENABLED] = enabled
            it[Keys.DND_START_MIN] = startMinute.coerceIn(0, 1439)
            it[Keys.DND_END_MIN] = endMinute.coerceIn(0, 1439)
            it[Keys.DND_ALLOW_CRITICAL] = allowCritical
        }
    }

    suspend fun setSortOption(option: ServerSortOption) {
        context.dataStore.edit { it[Keys.SORT_OPTION] = option.name }
    }
}
