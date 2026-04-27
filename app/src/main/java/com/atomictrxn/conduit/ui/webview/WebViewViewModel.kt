package com.atomictrxn.conduit.ui.webview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atomictrxn.conduit.data.repository.ServerRepository
import com.atomictrxn.conduit.domain.model.ConnectionState
import com.atomictrxn.conduit.domain.model.ServerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebViewViewModel
    @Inject
    constructor(
        private val repository: ServerRepository,
    ) : ViewModel() {
        val serverConfig: StateFlow<ServerConfig> =
            repository.serverConfig
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerConfig())

        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Loading)
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

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
    }
