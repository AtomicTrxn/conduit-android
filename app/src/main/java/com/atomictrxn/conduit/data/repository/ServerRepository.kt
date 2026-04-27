package com.atomictrxn.conduit.data.repository

import com.atomictrxn.conduit.data.local.SettingsDataStore
import com.atomictrxn.conduit.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(
    private val dataStore: SettingsDataStore
) {
    val serverConfig: Flow<ServerConfig> = dataStore.serverConfig
    val onboardingComplete: Flow<Boolean> = dataStore.onboardingComplete
    val notificationsEnabled: Flow<Boolean> = dataStore.notificationsEnabled

    suspend fun saveServerConfig(config: ServerConfig) = dataStore.saveServerConfig(config)
    suspend fun saveServerUrl(url: String) = dataStore.saveServerUrl(url)
    suspend fun saveApiKey(apiKey: String) = dataStore.saveApiKey(apiKey)
    suspend fun setOnboardingComplete(complete: Boolean) = dataStore.setOnboardingComplete(complete)
    suspend fun setNotificationsEnabled(enabled: Boolean) = dataStore.setNotificationsEnabled(enabled)
}
