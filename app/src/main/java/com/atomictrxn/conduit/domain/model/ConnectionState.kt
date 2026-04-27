package com.atomictrxn.conduit.domain.model

sealed interface ConnectionState {
    data object Loading : ConnectionState

    data object Connected : ConnectionState

    data class Error(val url: String, val message: String? = null) : ConnectionState
}
