package com.atomictrxn.conduit.data.api.models

import com.google.gson.annotations.SerializedName

data class ApiKeyResponse(
    @SerializedName("api_key") val apiKey: String = "",
)

data class ChatListItem(
    val id: String = "",
    val title: String = "",
    @SerializedName("updated_at") val updatedAt: Long = 0,
)
