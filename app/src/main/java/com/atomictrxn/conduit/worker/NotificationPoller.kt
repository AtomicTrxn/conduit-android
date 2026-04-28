package com.atomictrxn.conduit.worker

import com.atomictrxn.conduit.data.api.OpenWebUIServiceFactory
import com.atomictrxn.conduit.data.api.models.ChatListItem
import com.atomictrxn.conduit.data.repository.ConduitRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface NotificationPollResult {
    data class Success(val chats: List<ChatListItem>) : NotificationPollResult

    data object Retry : NotificationPollResult
}

class NotificationPoller
    @Inject
    constructor(
        private val repository: ConduitRepository,
        private val serviceFactory: OpenWebUIServiceFactory,
    ) {
        suspend fun poll(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): NotificationPollResult {
            val config = repository.serverConfig.first()
            if (!config.hasApiKey) return NotificationPollResult.Success(emptyList())

            val notificationsEnabled = repository.notificationsEnabled.first()
            if (!notificationsEnabled) return NotificationPollResult.Success(emptyList())

            return try {
                val lastChecked = repository.lastNotificationCheck.first()

                if (lastChecked == 0L) {
                    repository.setLastNotificationCheck(nowEpochSeconds)
                    return NotificationPollResult.Success(emptyList())
                }

                val chats = serviceFactory.create(config.serverUrl, config.apiKey).getChats()
                val updatedChats = chats.filter { it.updatedAt > lastChecked }.take(MAX_NOTIFICATIONS)
                repository.setLastNotificationCheck(nowEpochSeconds)
                NotificationPollResult.Success(updatedChats)
            } catch (e: Exception) {
                NotificationPollResult.Retry
            }
        }

        private companion object {
            const val MAX_NOTIFICATIONS = 10
        }
    }
