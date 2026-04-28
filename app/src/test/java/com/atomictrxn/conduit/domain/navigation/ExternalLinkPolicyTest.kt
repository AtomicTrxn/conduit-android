package com.atomictrxn.conduit.domain.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalLinkPolicyTest {
    @Test
    fun keepsConfiguredOpenWebUIUrlsInWebView() {
        assertEquals(
            ExternalLinkAction.KeepInWebView,
            ExternalLinkPolicy.decide("https://openwebui.example.com", "https://openwebui.example.com/c/chat"),
        )
    }

    @Test
    fun downloadsFileLikeExternalUrls() {
        assertEquals(
            ExternalLinkAction.Download,
            ExternalLinkPolicy.decide("https://openwebui.example.com", "http://192.168.50.122:9000/response.txt"),
        )
    }

    @Test
    fun opensOrdinaryExternalHttpUrlsExternally() {
        assertEquals(
            ExternalLinkAction.OpenExternally,
            ExternalLinkPolicy.decide("https://openwebui.example.com", "https://example.com/docs"),
        )
    }

    @Test
    fun blocksNonHttpUrls() {
        assertEquals(
            ExternalLinkAction.Block("mailto"),
            ExternalLinkPolicy.decide("https://openwebui.example.com", "mailto:test@example.com"),
        )
    }
}
