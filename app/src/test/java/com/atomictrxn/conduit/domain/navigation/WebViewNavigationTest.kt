package com.atomictrxn.conduit.domain.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewNavigationTest {
    @Test
    fun chatLocationForAcceptsChatUrls() {
        assertEquals(
            ChatLocation("abc123", "https://openwebui.example.com/c/abc123"),
            WebViewNavigation.chatLocationFor("https://openwebui.example.com", "https://openwebui.example.com/c/abc123"),
        )
        assertEquals(
            ChatLocation("chat with spaces", "https://openwebui.example.com/c/chat%20with%20spaces"),
            WebViewNavigation.chatLocationFor(
                "https://openwebui.example.com",
                "https://openwebui.example.com/c/chat%20with%20spaces",
            ),
        )
    }

    @Test
    fun chatLocationForRejectsNonChatUrls() {
        assertNull(WebViewNavigation.chatLocationFor("https://openwebui.example.com", "https://openwebui.example.com/"))
        assertNull(WebViewNavigation.chatLocationFor("https://openwebui.example.com", "https://other.example.com/c/abc123"))
        assertNull(WebViewNavigation.chatLocationFor("https://openwebui.example.com", "https://openwebui.example.com/workspace/abc123"))
    }

    @Test
    fun selectResumeUrlPrefersPersistedChatThenNewestChatThenRoot() {
        assertEquals(
            "https://openwebui.example.com/c/old",
            WebViewNavigation.selectResumeUrl("https://openwebui.example.com", "newest", "https://openwebui.example.com/c/old"),
        )
        assertEquals(
            "https://openwebui.example.com/c/old",
            WebViewNavigation.selectResumeUrl("https://openwebui.example.com", null, "https://openwebui.example.com/c/old"),
        )
        assertEquals(
            "https://openwebui.example.com/c/newest",
            WebViewNavigation.selectResumeUrl("https://openwebui.example.com", "newest", null),
        )
        assertEquals(
            "https://openwebui.example.com",
            WebViewNavigation.selectResumeUrl("https://openwebui.example.com", null, null),
        )
        assertEquals(
            "https://openwebui.example.com/c/newest",
            WebViewNavigation.selectResumeUrl("https://openwebui.example.com", "newest", "https://other.example.com/c/old"),
        )
    }

    @Test
    fun fileLikeUrlDetectionHandlesArtifacts() {
        assertTrue(WebViewNavigation.isFileLikeUrl("http://192.168.50.122:9000/response.txt"))
        assertTrue(WebViewNavigation.isFileLikeUrl("https://example.com/artifact.json"))
        assertTrue(WebViewNavigation.isFileLikeUrl("https://example.com/download.tar.gz"))
        assertFalse(WebViewNavigation.isFileLikeUrl("https://example.com/docs/"))
        assertFalse(WebViewNavigation.isFileLikeUrl("https://example.com/chat"))
    }
}
