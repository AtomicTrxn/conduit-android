package com.atomictrxn.conduit.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.atomictrxn.conduit.domain.model.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "conduit_settings")

@Singleton
class SettingsDataStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private object Keys {
            val SERVER_URL = stringPreferencesKey("server_url")
            val API_KEY = stringPreferencesKey("api_key")
            val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
            val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
            val LAST_NOTIFICATION_CHECK = longPreferencesKey("last_notification_check")
        }

        val serverConfig: Flow<ServerConfig> =
            context.dataStore.data.map { prefs ->
                ServerConfig(
                    serverUrl = prefs[Keys.SERVER_URL] ?: "",
                    apiKey = prefs[Keys.API_KEY] ?: "",
                )
            }

        val onboardingComplete: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.ONBOARDING_COMPLETE] ?: false
            }

        val notificationsEnabled: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
            }

        val lastNotificationCheck: Flow<Long> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.LAST_NOTIFICATION_CHECK] ?: 0L
            }

        suspend fun saveServerConfig(config: ServerConfig) {
            context.dataStore.edit { prefs ->
                prefs[Keys.SERVER_URL] = config.serverUrl
                prefs[Keys.API_KEY] = config.apiKey
            }
        }

        suspend fun saveServerUrl(url: String) {
            context.dataStore.edit { it[Keys.SERVER_URL] = url }
        }

        suspend fun saveApiKey(apiKey: String) {
            context.dataStore.edit { it[Keys.API_KEY] = apiKey }
        }

        suspend fun setOnboardingComplete(complete: Boolean) {
            context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
        }

        suspend fun setNotificationsEnabled(enabled: Boolean) {
            context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
        }

        suspend fun setLastNotificationCheck(timestamp: Long) {
            context.dataStore.edit { it[Keys.LAST_NOTIFICATION_CHECK] = timestamp }
        }
    }
