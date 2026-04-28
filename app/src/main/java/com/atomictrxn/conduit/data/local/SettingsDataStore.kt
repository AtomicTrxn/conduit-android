package com.atomictrxn.conduit.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.atomictrxn.conduit.domain.model.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "conduit_settings")
private const val SECURE_PREFS_FILE = "conduit_secure"
private const val API_KEY_PREF = "api_key"

@Singleton
class SettingsDataStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private object Keys {
            val SERVER_URL = stringPreferencesKey("server_url")
            val API_KEY_LEGACY = stringPreferencesKey("api_key")
            val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
            val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
            val LAST_NOTIFICATION_CHECK = longPreferencesKey("last_notification_check")
            val LAST_CHAT_ID = stringPreferencesKey("last_chat_id")
            val LAST_CHAT_URL = stringPreferencesKey("last_chat_url")
        }

        private val encryptedPrefs: SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_FILE,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        private val apiKeyState = MutableStateFlow(encryptedPrefs.getString(API_KEY_PREF, "") ?: "")
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        init {
            // One-time migration: move API key from plaintext DataStore → EncryptedSharedPreferences.
            if (encryptedPrefs.getString(API_KEY_PREF, null) == null) {
                scope.launch {
                    context.dataStore.data.map { it[Keys.API_KEY_LEGACY] }.collect { legacy ->
                        if (!legacy.isNullOrEmpty()) {
                            apiKeyState.value = legacy
                            encryptedPrefs.edit().putString(API_KEY_PREF, legacy).apply()
                            context.dataStore.edit { it.remove(Keys.API_KEY_LEGACY) }
                        }
                        return@collect
                    }
                }
            }
            encryptedPrefs.registerOnSharedPreferenceChangeListener { _, key ->
                if (key == API_KEY_PREF) {
                    apiKeyState.value = encryptedPrefs.getString(API_KEY_PREF, "") ?: ""
                }
            }
        }

        val serverConfig: Flow<ServerConfig> =
            context.dataStore.data.combine(apiKeyState) { prefs, apiKey ->
                ServerConfig(
                    serverUrl = prefs[Keys.SERVER_URL] ?: "",
                    apiKey = apiKey,
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

        val lastChatUrl: Flow<String> =
            context.dataStore.data.map { prefs ->
                prefs[Keys.LAST_CHAT_URL] ?: ""
            }

        suspend fun saveServerConfig(config: ServerConfig) {
            context.dataStore.edit { prefs ->
                val urlChanged = prefs[Keys.SERVER_URL] != config.serverUrl
                prefs[Keys.SERVER_URL] = config.serverUrl
                if (urlChanged) prefs[Keys.LAST_NOTIFICATION_CHECK] = 0L
            }
            saveApiKey(config.apiKey)
        }

        suspend fun saveServerUrl(url: String) {
            context.dataStore.edit { prefs ->
                val urlChanged = prefs[Keys.SERVER_URL] != url
                prefs[Keys.SERVER_URL] = url
                if (urlChanged) prefs[Keys.LAST_NOTIFICATION_CHECK] = 0L
            }
        }

        suspend fun saveApiKey(apiKey: String) {
            apiKeyState.value = apiKey
            if (apiKey.isEmpty()) {
                encryptedPrefs.edit().remove(API_KEY_PREF).apply()
            } else {
                encryptedPrefs.edit().putString(API_KEY_PREF, apiKey).apply()
            }
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

        suspend fun saveLastChat(
            chatId: String,
            chatUrl: String,
        ) {
            context.dataStore.edit {
                it[Keys.LAST_CHAT_ID] = chatId
                it[Keys.LAST_CHAT_URL] = chatUrl
            }
        }
    }
