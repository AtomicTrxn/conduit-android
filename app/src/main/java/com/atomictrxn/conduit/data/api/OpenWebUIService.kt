package com.atomictrxn.conduit.data.api

import com.atomictrxn.conduit.data.api.models.Chat
import com.atomictrxn.conduit.data.api.models.ChatListItem
import retrofit2.http.GET
import retrofit2.http.Path

interface OpenWebUIService {
    @GET("api/chats")
    suspend fun getChats(): List<ChatListItem>

    @GET("api/chats/{id}")
    suspend fun getChat(
        @Path("id") id: String,
    ): Chat
}
