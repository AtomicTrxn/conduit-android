package com.atomictrxn.conduit.data.repository

import com.atomictrxn.conduit.data.local.SettingsDataStore
import com.atomictrxn.conduit.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository
    @Inject
    constructor(
        private val dataStore: SettingsDataStore,
    ) : ConduitRepository {
        override val serverConfig: Flow<ServerConfig> = dataStore.serverConfig
        override val onboardingComplete: Flow<Boolean> = dataStore.onboardingComplete
        override val notificationsEnabled: Flow<Boolean> = dataStore.notificationsEnabled
        override val lastNotificationCheck: Flow<Long> = dataStore.lastNotificationCheck
        override val lastChatUrl: Flow<String> = dataStore.lastChatUrl

        override suspend fun saveServerConfig(config: ServerConfig) = dataStore.saveServerConfig(config)

        override suspend fun saveServerUrl(url: String) = dataStore.saveServerUrl(url)

        override suspend fun saveApiKey(apiKey: String) = dataStore.saveApiKey(apiKey)

        override suspend fun setOnboardingComplete(complete: Boolean) = dataStore.setOnboardingComplete(complete)

        override suspend fun setNotificationsEnabled(enabled: Boolean) = dataStore.setNotificationsEnabled(enabled)

        override suspend fun setLastNotificationCheck(timestamp: Long) = dataStore.setLastNotificationCheck(timestamp)

        override suspend fun saveLastChat(
            chatId: String,
            chatUrl: String,
        ) = dataStore.saveLastChat(chatId, chatUrl)
    }
