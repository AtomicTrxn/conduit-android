package com.atomictrxn.conduit.ui.webview

import com.atomictrxn.conduit.data.api.models.ChatListItem
import com.atomictrxn.conduit.domain.model.ConnectionState
import com.atomictrxn.conduit.domain.model.ServerConfig
import com.atomictrxn.conduit.test.FakeConduitRepository
import com.atomictrxn.conduit.test.FakeOpenWebUIService
import com.atomictrxn.conduit.test.FakeOpenWebUIServiceFactory
import com.atomictrxn.conduit.test.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WebViewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialUrlUsesPersistedLastChatWithoutCallingApi() =
        runTest {
            val repository =
                FakeConduitRepository(
                    lastChatUrl = "https://openwebui.example.com/c/old",
                )
            val factory = FakeOpenWebUIServiceFactory()
            val viewModel = WebViewViewModel(repository, factory)

            val result = viewModel.initialUrlFor(ServerConfig("https://openwebui.example.com", "api-key"))

            assertEquals("https://openwebui.example.com/c/old", result)
            assertEquals(0, factory.createCalls)
        }

    @Test
    fun initialUrlUsesNewestApiChatWhenPersistedChatIsMissing() =
        runTest {
            val factory =
                FakeOpenWebUIServiceFactory(
                    FakeOpenWebUIService(
                        chatsProvider = {
                            listOf(
                                ChatListItem(id = "older", updatedAt = 10),
                                ChatListItem(id = "newer", updatedAt = 20),
                            )
                        },
                    ),
                )
            val viewModel = WebViewViewModel(FakeConduitRepository(), factory)

            val result = viewModel.initialUrlFor(ServerConfig("https://openwebui.example.com", "api-key"))

            assertEquals("https://openwebui.example.com/c/newer", result)
            assertEquals(1, factory.createCalls)
        }

    @Test
    fun initialUrlFallsBackToRootWhenApiFails() =
        runTest {
            val factory =
                FakeOpenWebUIServiceFactory(
                    FakeOpenWebUIService(
                        chatsProvider = {
                            error("boom")
                        },
                    ),
                )
            val viewModel = WebViewViewModel(FakeConduitRepository(), factory)

            val result = viewModel.initialUrlFor(ServerConfig("https://openwebui.example.com", "api-key"))

            assertEquals("https://openwebui.example.com", result)
        }

    @Test
    fun pageTitleIsTrimmed() {
        val viewModel = WebViewViewModel(FakeConduitRepository(), FakeOpenWebUIServiceFactory())

        viewModel.onPageTitleChanged("  Chat title  ")

        assertEquals("Chat title", viewModel.pageTitle.value)
    }

    @Test
    fun connectionStateTracksLoadingConnectedAndError() {
        val viewModel = WebViewViewModel(FakeConduitRepository(), FakeOpenWebUIServiceFactory())

        viewModel.onPageStarted()
        assertEquals(ConnectionState.Loading, viewModel.connectionState.value)

        viewModel.onPageFinished()
        assertEquals(ConnectionState.Connected, viewModel.connectionState.value)

        viewModel.onConnectionError("https://openwebui.example.com", "failed")
        assertTrue(viewModel.connectionState.value is ConnectionState.Error)
    }

    @Test
    fun showSettingsAndDismissTogglesState() {
        val viewModel = WebViewViewModel(FakeConduitRepository(), FakeOpenWebUIServiceFactory())

        viewModel.showSettings()
        assertTrue(viewModel.showSettings.value)

        viewModel.dismissSettings()
        assertFalse(viewModel.showSettings.value)
    }

    @Test
    fun showAboutAndDismissTogglesState() {
        val viewModel = WebViewViewModel(FakeConduitRepository(), FakeOpenWebUIServiceFactory())

        viewModel.showAbout()
        assertTrue(viewModel.showAbout.value)

        viewModel.dismissAbout()
        assertFalse(viewModel.showAbout.value)
    }

    @Test
    fun saveLastChatPersistsToRepository() =
        runTest {
            val repository = FakeConduitRepository()
            val viewModel = WebViewViewModel(repository, FakeOpenWebUIServiceFactory())

            viewModel.saveLastChat("chat-42", "https://openwebui.example.com/c/chat-42")

            assertEquals("chat-42" to "https://openwebui.example.com/c/chat-42", repository.savedLastChat)
        }

    @Test
    fun initialUrlFallsBackToRootWhenNoApiKey() =
        runTest {
            val viewModel = WebViewViewModel(FakeConduitRepository(), FakeOpenWebUIServiceFactory())

            val result = viewModel.initialUrlFor(ServerConfig("https://openwebui.example.com", ""))

            assertEquals("https://openwebui.example.com", result)
        }
}
