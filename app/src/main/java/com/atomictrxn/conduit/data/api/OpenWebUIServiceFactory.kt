package com.atomictrxn.conduit.data.api

import javax.inject.Inject
import javax.inject.Singleton

interface OpenWebUIServiceFactory {
    fun create(
        baseUrl: String,
        apiKey: String,
    ): OpenWebUIService
}

@Singleton
class DefaultOpenWebUIServiceFactory
    @Inject
    constructor() : OpenWebUIServiceFactory {
        override fun create(
            baseUrl: String,
            apiKey: String,
        ): OpenWebUIService = ApiClient.create(baseUrl, apiKey)
    }
