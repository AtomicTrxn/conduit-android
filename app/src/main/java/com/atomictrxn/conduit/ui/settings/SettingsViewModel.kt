package com.atomictrxn.conduit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomictrxn.conduit.data.repository.ServerRepository
import com.atomictrxn.conduit.domain.model.ServerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val notificationsEnabled: Boolean = true,
    val urlError: String? = null,
    val isSaved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: ServerRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        val serverConfig: StateFlow<ServerConfig> =
            repository.serverConfig
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerConfig())

        val notificationsEnabled: StateFlow<Boolean> =
            repository.notificationsEnabled
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        fun onServerUrlChanged(url: String) {
            _uiState.update { it.copy(serverUrl = url, urlError = null) }
        }

        fun onApiKeyChanged(apiKey: String) {
            _uiState.update { it.copy(apiKey = apiKey) }
        }

        fun loadCurrentConfig() {
            viewModelScope.launch {
                repository.serverConfig.first().let { config ->
                    _uiState.update {
                        it.copy(
                            serverUrl = config.serverUrl,
                            apiKey = config.apiKey,
                            notificationsEnabled = repository.notificationsEnabled.first(),
                        )
                    }
                }
            }
        }

        fun saveSettings(): Boolean {
            val url = _uiState.value.serverUrl.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                _uiState.update { it.copy(urlError = "URL must start with http:// or https://") }
                return false
            }
            viewModelScope.launch {
                repository.saveServerConfig(
                    ServerConfig(
                        serverUrl = url,
                        apiKey = _uiState.value.apiKey.trim(),
                    ),
                )
                repository.setNotificationsEnabled(_uiState.value.notificationsEnabled)
                _uiState.update { it.copy(isSaved = true) }
            }
            return true
        }

        fun setNotificationsEnabled(enabled: Boolean) {
            _uiState.update { it.copy(notificationsEnabled = enabled) }
        }

        fun clearApiKey() {
            _uiState.update { it.copy(apiKey = "") }
        }
    }
