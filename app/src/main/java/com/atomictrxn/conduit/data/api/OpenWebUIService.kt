package com.atomictrxn.conduit.data.api

import com.atomictrxn.conduit.data.api.models.ApiKeyResponse
import com.atomictrxn.conduit.data.api.models.ChatListItem
import retrofit2.http.GET

interface OpenWebUIService {
    @GET("api/chats")
    suspend fun getChats(): List<ChatListItem>

    @GET("api/v1/auths/api_key")
    suspend fun getApiKey(): ApiKeyResponse
}
