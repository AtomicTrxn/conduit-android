package com.atomictrxn.conduit.data.api

import com.atomictrxn.conduit.data.api.models.ApiKeyResponse
import com.atomictrxn.conduit.data.api.models.ChatListItem
import retrofit2.http.GET
import retrofit2.http.POST

interface OpenWebUIService {
    @GET("api/v1/chats/")
    suspend fun getChats(): List<ChatListItem>

    @POST("api/v1/auths/api_key")
    suspend fun getApiKey(): ApiKeyResponse
}
