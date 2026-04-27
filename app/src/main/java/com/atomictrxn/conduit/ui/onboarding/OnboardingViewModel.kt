package com.atomictrxn.conduit.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomictrxn.conduit.data.repository.ServerRepository
import com.atomictrxn.conduit.domain.model.ServerConfig
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
    val isComplete: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: ServerRepository
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
        if (url.isBlank()) {
            _uiState.update { it.copy(urlError = "Please enter a server URL") }
            return false
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _uiState.update { it.copy(urlError = "URL must start with http:// or https://") }
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
                    apiKey = state.apiKey.trim()
                )
            )
            repository.setOnboardingComplete(true)
            _uiState.update { it.copy(isComplete = true) }
        }
    }

    fun skipApiKey() {
        completeOnboarding()
    }
}
