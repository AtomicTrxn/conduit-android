package com.atomictrxn.conduit.data.api.models

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    val id: String = "",
    val role: String = "",
    val content: String = "",
)

data class Chat(
    val id: String = "",
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    @SerializedName("updated_at") val updatedAt: Long = 0,
)

data class ChatListItem(
    val id: String = "",
    val title: String = "",
    @SerializedName("updated_at") val updatedAt: Long = 0,
)
