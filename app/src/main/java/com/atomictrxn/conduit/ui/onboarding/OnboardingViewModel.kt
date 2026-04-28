package com.atomictrxn.conduit.ui.onboarding

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val urlError: String? = null,
    val isComplete: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val repository: ConduitRepository,
        private val strings: StringProvider,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OnboardingUiState())
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        fun onServerUrlChanged(url: String) {
            _uiState.update { it.copy(serverUrl = url, urlError = null) }
        }

        fun onApiKeyChanged(apiKey: String) {
            _uiState.update { it.copy(apiKey = apiKey) }
        }

        fun submitServerUrl(): Boolean {
            val url = _uiState.value.serverUrl.trim()
            val validation = ServerUrlValidator.validate(url)
            if (validation != ServerUrlValidationResult.Valid) {
                _uiState.update { it.copy(urlError = strings.getString(validation.errorStringRes())) }
                return false
            }
            _uiState.update { it.copy(serverUrl = url, urlError = null) }
            return true
        }

        fun completeOnboarding() {
            viewModelScope.launch {
                val state = _uiState.value
                repository.saveServerConfig(
                    ServerConfig(
                        serverUrl = state.serverUrl.trim(),
                        apiKey = state.apiKey.trim(),
                    ),
                )
                repository.setOnboardingComplete(true)
                _uiState.update { it.copy(isComplete = true) }
            }
        }

        fun skipApiKey() {
            completeOnboarding()
        }

        private fun ServerUrlValidationResult.errorStringRes(): Int =
            when (this) {
                ServerUrlValidationResult.Valid -> R.string.url_invalid
                ServerUrlValidationResult.Required -> R.string.url_required
                ServerUrlValidationResult.InvalidScheme -> R.string.url_invalid
                ServerUrlValidationResult.PublicHttp -> R.string.url_public_http_invalid
            }
    }
