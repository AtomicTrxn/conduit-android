package com.atomictrxn.conduit.ui.webview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomictrxn.conduit.data.api.OpenWebUIServiceFactory
import com.atomictrxn.conduit.data.repository.ConduitRepository
import com.atomictrxn.conduit.domain.model.ConnectionState
import com.atomictrxn.conduit.domain.model.ServerConfig
import com.atomictrxn.conduit.domain.navigation.WebViewNavigation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class WebViewViewModel
    @Inject
    constructor(
        private val repository: ConduitRepository,
        private val serviceFactory: OpenWebUIServiceFactory,
    ) : ViewModel() {
        val serverConfig: StateFlow<ServerConfig> =
            repository.serverConfig
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerConfig())

        val lastChatUrl: StateFlow<String> =
            repository.lastChatUrl
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Loading)
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _pageTitle = MutableStateFlow("")
        val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

        private val _showSettings = MutableStateFlow(false)
        val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

        private val _showAbout = MutableStateFlow(false)
        val showAbout: StateFlow<Boolean> = _showAbout.asStateFlow()

        fun onPageStarted() {
            _connectionState.update { ConnectionState.Loading }
        }

        fun onPageFinished() {
            _connectionState.update { ConnectionState.Connected }
        }

        fun onPageTitleChanged(title: String?) {
            _pageTitle.update { title.orEmpty().trim() }
        }

        fun onConnectionError(
            url: String,
            message: String? = null,
        ) {
            _connectionState.update { ConnectionState.Error(url, message) }
        }

        fun showSettings() {
            _showSettings.update { true }
        }

        fun dismissSettings() {
            _showSettings.update { false }
        }

        fun showAbout() {
            _showAbout.update { true }
        }

        fun dismissAbout() {
            _showAbout.update { false }
        }

        fun saveServerUrl(url: String) {
            viewModelScope.launch {
                repository.saveServerUrl(url)
            }
        }

        fun saveApiKey(apiKey: String) {
            viewModelScope.launch {
                repository.saveApiKey(apiKey)
            }
        }

        fun saveLastChat(
            chatId: String,
            chatUrl: String,
        ) {
            viewModelScope.launch {
                repository.saveLastChat(chatId, chatUrl)
            }
        }

        suspend fun initialUrlFor(config: ServerConfig): String {
            val persistedLastChatUrl = repository.lastChatUrl.first()
            if (isValidLastChatUrl(config.serverUrl, persistedLastChatUrl)) {
                return persistedLastChatUrl
            }

            val latestChatId = latestChatId(config)
            return WebViewNavigation.selectResumeUrl(
                serverUrl = config.serverUrl,
                latestChatId = latestChatId,
                lastChatUrl = persistedLastChatUrl,
            )
        }

        private fun isValidLastChatUrl(
            serverUrl: String,
            lastChatUrl: String,
        ): Boolean {
            val normalizedServerUrl = serverUrl.trimEnd('/')
            return normalizedServerUrl.isNotBlank() && lastChatUrl.startsWith(normalizedServerUrl)
        }

        private suspend fun latestChatId(config: ServerConfig): String? {
            if (!config.hasApiKey || config.serverUrl.isBlank()) return null
            return withContext(Dispatchers.IO) {
                runCatching {
                    serviceFactory.create(config.serverUrl, config.apiKey)
                        .getChats()
                        .maxByOrNull { it.updatedAt }
                        ?.id
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
            }
        }
    }
