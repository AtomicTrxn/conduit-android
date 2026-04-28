package com.atomictrxn.conduit.test

import com.atomictrxn.conduit.data.api.OpenWebUIService
import com.atomictrxn.conduit.data.api.OpenWebUIServiceFactory
import com.atomictrxn.conduit.data.api.models.ApiKeyResponse
import com.atomictrxn.conduit.data.api.models.ChatListItem
import com.atomictrxn.conduit.data.repository.ConduitRepository
import com.atomictrxn.conduit.domain.model.ServerConfig
import com.atomictrxn.conduit.ui.common.StringProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeConduitRepository(
    initialConfig: ServerConfig = ServerConfig(),
    onboardingComplete: Boolean = false,
    notificationsEnabled: Boolean = true,
    lastNotificationCheck: Long = 0L,
    lastChatUrl: String = "",
) : ConduitRepository {
    private val serverConfigState = MutableStateFlow(initialConfig)
    private val onboardingCompleteState = MutableStateFlow(onboardingComplete)
    private val notificationsEnabledState = MutableStateFlow(notificationsEnabled)
    private val lastNotificationCheckState = MutableStateFlow(lastNotificationCheck)
    private val lastChatUrlState = MutableStateFlow(lastChatUrl)

    override val serverConfig: Flow<ServerConfig> = serverConfigState
    override val onboardingComplete: Flow<Boolean> = onboardingCompleteState
    override val notificationsEnabled: Flow<Boolean> = notificationsEnabledState
    override val lastNotificationCheck: Flow<Long> = lastNotificationCheckState
    override val lastChatUrl: Flow<String> = lastChatUrlState

    var savedConfig: ServerConfig? = null
        private set
    var savedApiKey: String? = null
        private set
    var savedLastChat: Pair<String, String>? = null
        private set

    val currentConfig: ServerConfig get() = serverConfigState.value
    val currentOnboardingComplete: Boolean get() = onboardingCompleteState.value
    val currentNotificationsEnabled: Boolean get() = notificationsEnabledState.value
    val currentLastNotificationCheck: Long get() = lastNotificationCheckState.value
    val currentLastChatUrl: String get() = lastChatUrlState.value

    override suspend fun saveServerConfig(config: ServerConfig) {
        savedConfig = config
        serverConfigState.value = config
    }

    override suspend fun saveServerUrl(url: String) {
        serverConfigState.value = serverConfigState.value.copy(serverUrl = url)
    }

    override suspend fun saveApiKey(apiKey: String) {
        savedApiKey = apiKey
        serverConfigState.value = serverConfigState.value.copy(apiKey = apiKey)
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        onboardingCompleteState.value = complete
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabledState.value = enabled
    }

    override suspend fun setLastNotificationCheck(timestamp: Long) {
        lastNotificationCheckState.value = timestamp
    }

    override suspend fun saveLastChat(
        chatId: String,
        chatUrl: String,
    ) {
        savedLastChat = chatId to chatUrl
        lastChatUrlState.value = chatUrl
    }
}

class FakeOpenWebUIServiceFactory(
    private val service: OpenWebUIService = FakeOpenWebUIService(),
) : OpenWebUIServiceFactory {
    var createCalls = 0
        private set

    override fun create(
        baseUrl: String,
        apiKey: String,
    ): OpenWebUIService {
        createCalls += 1
        return service
    }
}

class FakeOpenWebUIService(
    private val chatsProvider: suspend () -> List<ChatListItem> = { emptyList() },
    private val apiKeyProvider: suspend () -> ApiKeyResponse = { ApiKeyResponse("api-key") },
) : OpenWebUIService {
    override suspend fun getChats(): List<ChatListItem> = chatsProvider()

    override suspend fun getApiKey(): ApiKeyResponse = apiKeyProvider()
}

class FakeStringProvider : StringProvider {
    override fun getString(resId: Int): String = "string-$resId"
}
