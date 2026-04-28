package com.atomictrxn.conduit.ui.settings

import com.atomictrxn.conduit.domain.model.ServerConfig
import com.atomictrxn.conduit.test.FakeConduitRepository
import com.atomictrxn.conduit.test.FakeStringProvider
import com.atomictrxn.conduit.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadCurrentConfigPopulatesState() =
        runTest {
            val repository =
                FakeConduitRepository(
                    initialConfig = ServerConfig("https://openwebui.example.com", "api-key"),
                    notificationsEnabled = false,
                )
            val viewModel = SettingsViewModel(repository, FakeStringProvider())

            viewModel.loadCurrentConfig()
            advanceUntilIdle()

            assertEquals("https://openwebui.example.com", viewModel.uiState.value.serverUrl)
            assertEquals("api-key", viewModel.uiState.value.apiKey)
            assertFalse(viewModel.uiState.value.notificationsEnabled)
        }

    @Test
    fun invalidPublicHttpUrlBlocksSave() {
        val viewModel = SettingsViewModel(FakeConduitRepository(), FakeStringProvider())

        viewModel.onServerUrlChanged("http://example.com")

        assertFalse(viewModel.saveSettings())
        assertNotNull(viewModel.uiState.value.urlError)
    }

    @Test
    fun validSaveTrimsValuesAndPersistsNotificationToggle() =
        runTest {
            val repository = FakeConduitRepository(initialConfig = ServerConfig("https://old.example.com", "old"))
            val viewModel = SettingsViewModel(repository, FakeStringProvider())

            viewModel.onServerUrlChanged(" https://openwebui.example.com ")
            viewModel.onApiKeyChanged(" api-key ")
            viewModel.setNotificationsEnabled(false)

            assertTrue(viewModel.saveSettings())
            advanceUntilIdle()

            assertEquals(ServerConfig("https://openwebui.example.com", "api-key"), repository.savedConfig)
            assertFalse(repository.currentNotificationsEnabled)
            assertTrue(viewModel.uiState.value.isSaved)
            assertTrue(viewModel.uiState.value.urlChanged)
        }

    @Test
    fun urlChangedIsFalseWhenServerUrlIsUnchanged() =
        runTest {
            val repository = FakeConduitRepository(initialConfig = ServerConfig("https://openwebui.example.com", "old"))
            val viewModel = SettingsViewModel(repository, FakeStringProvider())

            viewModel.loadCurrentConfig()
            advanceUntilIdle()
            viewModel.onServerUrlChanged("https://openwebui.example.com")
            viewModel.onApiKeyChanged("new")

            assertTrue(viewModel.saveSettings())
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.urlChanged)
        }

    @Test
    fun clearApiKeyUpdatesState() {
        val viewModel = SettingsViewModel(FakeConduitRepository(), FakeStringProvider())

        viewModel.onApiKeyChanged("api-key")
        viewModel.clearApiKey()

        assertEquals("", viewModel.uiState.value.apiKey)
    }

    @Test
    fun blankUrlBlocksSave() {
        val viewModel = SettingsViewModel(FakeConduitRepository(), FakeStringProvider())

        viewModel.onServerUrlChanged("")

        assertFalse(viewModel.saveSettings())
        assertNotNull(viewModel.uiState.value.urlError)
    }

    @Test
    fun isSavedFlagResetsOnSubsequentLoad() =
        runTest {
            val repository = FakeConduitRepository(initialConfig = ServerConfig("https://openwebui.example.com", ""))
            val viewModel = SettingsViewModel(repository, FakeStringProvider())

            viewModel.onServerUrlChanged("https://openwebui.example.com")
            assertTrue(viewModel.saveSettings())
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isSaved)

            viewModel.loadCurrentConfig()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSaved)
        }
}
