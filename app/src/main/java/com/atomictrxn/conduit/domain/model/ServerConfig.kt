package com.atomictrxn.conduit.domain.model

data class ServerConfig(
    val serverUrl: String = "",
    val apiKey: String = "",
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
    val isConfigured: Boolean get() = serverUrl.isNotBlank()
}
