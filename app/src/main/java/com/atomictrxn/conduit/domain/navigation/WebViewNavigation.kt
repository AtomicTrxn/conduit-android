package com.atomictrxn.conduit.domain.navigation

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

data class ChatLocation(
    val id: String,
    val url: String,
)

object WebViewNavigation {
    fun chatLocationFor(
        serverUrl: String,
        url: String,
    ): ChatLocation? {
        val normalizedServerUrl = serverUrl.trimEnd('/')
        if (normalizedServerUrl.isBlank() || !url.startsWith(normalizedServerUrl)) return null

        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val pathSegments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
        val chatId =
            pathSegments.getOrNull(1)
                ?.takeIf { pathSegments.firstOrNull() == "c" }
                ?.let { decodePathSegment(it) }
                ?: return null
        return ChatLocation(
            id = chatId,
            url = chatUrl(normalizedServerUrl, chatId),
        )
    }

    fun chatUrl(
        serverUrl: String,
        chatId: String,
    ): String = "${serverUrl.trimEnd('/')}/c/${encodePathSegment(chatId.take(128))}"

    fun isFileLikeUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val lastSegment = uri.path.orEmpty().substringAfterLast('/')
        if (lastSegment.isBlank() || lastSegment.endsWith("/")) return false

        val fileName = lastSegment.substringBefore('?').substringBefore('#')
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        return extension.length in 1..10 && extension.all { it.isLetterOrDigit() }
    }

    fun selectResumeUrl(
        serverUrl: String,
        latestChatId: String?,
        lastChatUrl: String?,
    ): String {
        val normalizedServerUrl = serverUrl.trimEnd('/')
        val normalizedLastChatUrl = lastChatUrl.orEmpty()
        if (normalizedServerUrl.isNotBlank() && normalizedLastChatUrl.startsWith(normalizedServerUrl)) {
            return normalizedLastChatUrl
        }

        if (!latestChatId.isNullOrBlank()) return chatUrl(serverUrl, latestChatId)

        return serverUrl
    }

    fun isHttpUrl(url: String): Boolean {
        val scheme = runCatching { URI(url).scheme?.lowercase(Locale.US) }.getOrNull()
        return scheme == "http" || scheme == "https"
    }

    private fun encodePathSegment(segment: String): String = URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

    private fun decodePathSegment(segment: String): String = URLDecoder.decode(segment, Charsets.UTF_8.name())
}
