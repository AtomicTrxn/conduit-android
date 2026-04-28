package com.atomictrxn.conduit.ui.onboarding

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun invalidPublicHttpUrlBlocksSubmit() {
        val viewModel = OnboardingViewModel(FakeConduitRepository(), FakeStringProvider())

        viewModel.onServerUrlChanged("http://example.com")

        assertFalse(viewModel.submitServerUrl())
        assertNotNull(viewModel.uiState.value.urlError)
    }

    @Test
    fun validUrlTrimsAndSubmits() {
        val viewModel = OnboardingViewModel(FakeConduitRepository(), FakeStringProvider())

        viewModel.onServerUrlChanged(" https://openwebui.example.com ")

        assertTrue(viewModel.submitServerUrl())
        assertEquals("https://openwebui.example.com", viewModel.uiState.value.serverUrl)
    }

    @Test
    fun completeOnboardingSavesTrimmedConfigAndMarksComplete() =
        runTest {
            val repository = FakeConduitRepository()
            val viewModel = OnboardingViewModel(repository, FakeStringProvider())

            viewModel.onServerUrlChanged(" https://openwebui.example.com ")
            viewModel.onApiKeyChanged(" api-key ")
            viewModel.completeOnboarding()
            advanceUntilIdle()

            assertEquals(ServerConfig("https://openwebui.example.com", "api-key"), repository.savedConfig)
            assertTrue(repository.currentOnboardingComplete)
            assertTrue(viewModel.uiState.value.isComplete)
        }

    @Test
    fun skipApiKeyCompletesWithBlankApiKey() =
        runTest {
            val repository = FakeConduitRepository()
            val viewModel = OnboardingViewModel(repository, FakeStringProvider())

            viewModel.onServerUrlChanged("https://openwebui.example.com")
            viewModel.skipApiKey()
            advanceUntilIdle()

            assertEquals(ServerConfig("https://openwebui.example.com", ""), repository.savedConfig)
            assertTrue(viewModel.uiState.value.isComplete)
        }

    @Test
    fun blankUrlBlocksSubmit() {
        val viewModel = OnboardingViewModel(FakeConduitRepository(), FakeStringProvider())

        viewModel.onServerUrlChanged("")

        assertFalse(viewModel.submitServerUrl())
        assertNotNull(viewModel.uiState.value.urlError)
    }

    @Test
    fun urlErrorClearedWhenUrlChanges() {
        val viewModel = OnboardingViewModel(FakeConduitRepository(), FakeStringProvider())

        viewModel.onServerUrlChanged("http://example.com")
        viewModel.submitServerUrl()
        assertNotNull(viewModel.uiState.value.urlError)

        viewModel.onServerUrlChanged("https://openwebui.example.com")
        assertNull(viewModel.uiState.value.urlError)
    }
}
