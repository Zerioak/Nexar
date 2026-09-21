package com.nexar.assistant.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexar_settings")

class NexarSettingsRepository(private val context: Context) {

    companion object {
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_FLOATING_ORB_ENABLED = booleanPreferencesKey("floating_orb_enabled")
        private val KEY_ORB_X = stringPreferencesKey("orb_x")
        private val KEY_ORB_Y = stringPreferencesKey("orb_y")
        private val KEY_VOICE_ACTIVATION = booleanPreferencesKey("voice_activation")
        private val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        private val KEY_ANNOUNCEMENT_NOTIFICATIONS = booleanPreferencesKey("announce_notifications")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_LANGUAGE_PREF = stringPreferencesKey("language_preference")
    }

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API_KEY] ?: ""
    }

    val floatingOrbEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FLOATING_ORB_ENABLED] ?: false
    }

    val orbPositionFlow: Flow<Pair<Int, Int>> = context.dataStore.data.map { prefs ->
        val x = prefs[KEY_ORB_X]?.toIntOrNull() ?: 100
        val y = prefs[KEY_ORB_Y]?.toIntOrNull() ?: 300
        Pair(x, y)
    }

    val voiceActivationFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_VOICE_ACTIVATION] ?: true
    }

    val autoReconnectFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_RECONNECT] ?: true
    }

    val announceNotificationsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ANNOUNCEMENT_NOTIFICATIONS] ?: false
    }

    val userNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_NAME] ?: "Hasbi"
    }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            if (apiKey.isBlank()) {
                prefs.remove(KEY_GEMINI_API_KEY)
            } else {
                prefs[KEY_GEMINI_API_KEY] = apiKey
            }
        }
    }

    suspend fun setFloatingOrbEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_ORB_ENABLED] = enabled
        }
    }

    suspend fun saveOrbPosition(x: Int, y: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ORB_X] = x.toString()
            prefs[KEY_ORB_Y] = y.toString()
        }
    }

    suspend fun setVoiceActivation(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VOICE_ACTIVATION] = enabled
        }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_RECONNECT] = enabled
        }
    }

    suspend fun setAnnounceNotifications(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ANNOUNCEMENT_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name
        }
    }

    suspend fun getApiKey(): String {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_GEMINI_API_KEY] ?: ""
        }.first()
    }
}
