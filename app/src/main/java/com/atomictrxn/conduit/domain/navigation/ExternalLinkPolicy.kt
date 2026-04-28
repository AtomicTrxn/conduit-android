package com.atomictrxn.conduit.domain.navigation

import java.net.URI
import java.util.Locale

sealed interface ExternalLinkAction {
    data object KeepInWebView : ExternalLinkAction

    data object Download : ExternalLinkAction

    data object OpenExternally : ExternalLinkAction

    data class Block(val scheme: String?) : ExternalLinkAction
}

object ExternalLinkPolicy {
    fun decide(
        currentServerUrl: String,
        targetUrl: String,
    ): ExternalLinkAction {
        if (currentServerUrl.isNotBlank() && targetUrl.startsWith(currentServerUrl.trimEnd('/'))) {
            return ExternalLinkAction.KeepInWebView
        }

        if (!WebViewNavigation.isHttpUrl(targetUrl)) {
            return ExternalLinkAction.Block(schemeFor(targetUrl))
        }

        return if (WebViewNavigation.isFileLikeUrl(targetUrl)) {
            ExternalLinkAction.Download
        } else {
            ExternalLinkAction.OpenExternally
        }
    }

    private fun schemeFor(url: String): String? =
        runCatching { URI(url).scheme?.lowercase(Locale.US) }
            .getOrNull()
}
