package com.atomictrxn.conduit.data.repository

import com.atomictrxn.conduit.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow

interface ConduitRepository {
    val serverConfig: Flow<ServerConfig>
    val onboardingComplete: Flow<Boolean>
    val notificationsEnabled: Flow<Boolean>
    val lastNotificationCheck: Flow<Long>
    val lastChatUrl: Flow<String>

    suspend fun saveServerConfig(config: ServerConfig)

    suspend fun saveServerUrl(url: String)

    suspend fun saveApiKey(apiKey: String)

    suspend fun setOnboardingComplete(complete: Boolean)

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun setLastNotificationCheck(timestamp: Long)

    suspend fun saveLastChat(
        chatId: String,
        chatUrl: String,
    )
}
