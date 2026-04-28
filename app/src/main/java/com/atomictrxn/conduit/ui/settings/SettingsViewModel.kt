package com.atomictrxn.conduit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomictrxn.conduit.R
import com.atomictrxn.conduit.data.repository.ConduitRepository
import com.atomictrxn.conduit.domain.model.ServerConfig
import com.atomictrxn.conduit.domain.validation.ServerUrlValidationResult
import com.atomictrxn.conduit.domain.validation.ServerUrlValidator
import com.atomictrxn.conduit.ui.common.StringProvider
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
    val urlChanged: Boolean = false,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: ConduitRepository,
        private val strings: StringProvider,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
        private var loadedServerUrl: String = ""

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
                    loadedServerUrl = config.serverUrl
                    _uiState.update {
                        it.copy(
                            serverUrl = config.serverUrl,
                            apiKey = config.apiKey,
                            notificationsEnabled = repository.notificationsEnabled.first(),
                            isSaved = false,
                        )
                    }
                }
            }
        }

        fun saveSettings(): Boolean {
            val url = _uiState.value.serverUrl.trim()
            val validation = ServerUrlValidator.validate(url)
            if (validation != ServerUrlValidationResult.Valid) {
                _uiState.update { it.copy(urlError = strings.getString(validation.errorStringRes())) }
                return false
            }
            val urlChanged = url != loadedServerUrl
            viewModelScope.launch {
                repository.saveServerConfig(
                    ServerConfig(
                        serverUrl = url,
                        apiKey = _uiState.value.apiKey.trim(),
                    ),
                )
                repository.setNotificationsEnabled(_uiState.value.notificationsEnabled)
                loadedServerUrl = url
                _uiState.update { it.copy(isSaved = true, urlChanged = urlChanged) }
            }
            return true
        }

        fun setNotificationsEnabled(enabled: Boolean) {
            _uiState.update { it.copy(notificationsEnabled = enabled) }
        }

        fun clearApiKey() {
            _uiState.update { it.copy(apiKey = "") }
        }

        private fun ServerUrlValidationResult.errorStringRes(): Int =
            when (this) {
                ServerUrlValidationResult.Valid -> R.string.url_invalid
                ServerUrlValidationResult.Required -> R.string.url_required
                ServerUrlValidationResult.InvalidScheme -> R.string.url_invalid
                ServerUrlValidationResult.PublicHttp -> R.string.url_public_http_invalid
            }
    }
