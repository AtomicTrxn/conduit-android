package com.atomictrxn.conduit.ui.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atomictrxn.conduit.domain.model.ServerConfig
import com.atomictrxn.conduit.test.ContextStringProvider
import com.atomictrxn.conduit.test.FakeConduitRepository
import com.atomictrxn.conduit.ui.theme.ConduitTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val strings get() = ContextStringProvider(context)

    private fun str(resId: Int) = context.getString(resId)

    private fun launchSettings(
        initialConfig: ServerConfig = ServerConfig("https://openwebui.example.com", ""),
        notificationsEnabled: Boolean = true,
        onDismiss: () -> Unit = {},
        onSave: () -> Unit = {},
        onSyncApiKey: () -> Unit = {},
    ): SettingsViewModel {
        val repository =
            FakeConduitRepository(
                initialConfig = initialConfig,
                notificationsEnabled = notificationsEnabled,
            )
        val viewModel = SettingsViewModel(repository, strings)
        viewModel.loadCurrentConfig()
        composeTestRule.setContent {
            ConduitTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onDismiss = onDismiss,
                    onSave = onSave,
                    onSyncApiKey = onSyncApiKey,
                )
            }
        }
        composeTestRule.waitForIdle()
        return viewModel
    }

    @Test
    fun settingsTitleIsDisplayed() {
        launchSettings()
        composeTestRule.onNodeWithText(str(com.atomictrxn.conduit.R.string.settings)).assertIsDisplayed()
    }

    @Test
    fun backButtonTriggersOnDismiss() {
        var dismissed = false
        launchSettings(onDismiss = { dismissed = true })
        composeTestRule
            .onNodeWithContentDescription(str(com.atomictrxn.conduit.R.string.navigate_back))
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun saveButtonCallsOnSaveWithValidUrl() {
        var saved = false
        launchSettings(
            initialConfig = ServerConfig("https://openwebui.example.com", ""),
            onSave = { saved = true },
        )
        composeTestRule.onNodeWithText(str(com.atomictrxn.conduit.R.string.save)).performClick()
        composeTestRule.waitForIdle()
        assertTrue(saved)
    }

    @Test
    fun urlErrorShownForPublicHttpUrl() {
        val viewModel = launchSettings()
        viewModel.onServerUrlChanged("http://example.com")
        viewModel.saveSettings()
        composeTestRule.waitForIdle()
        val errorText = viewModel.uiState.value.urlError
        composeTestRule.onNodeWithText(errorText!!).assertIsDisplayed()
    }

    @Test
    fun sessionApiKeyStatusLabelShown() {
        val jwt = "header.${android.util.Base64.encodeToString(
            """{"exp":9999999999}""".toByteArray(),
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE,
        )}.signature"
        launchSettings(initialConfig = ServerConfig("https://openwebui.example.com", jwt))
        composeTestRule
            .onNodeWithText(str(com.atomictrxn.conduit.R.string.api_key_status_session))
            .assertIsDisplayed()
    }

    @Test
    fun persistentApiKeyStatusLabelShown() {
        launchSettings(initialConfig = ServerConfig("https://openwebui.example.com", "sk-persistent-key"))
        composeTestRule
            .onNodeWithText(str(com.atomictrxn.conduit.R.string.api_key_status_persistent))
            .assertIsDisplayed()
    }

    @Test
    fun clearApiKeyButtonVisibleWhenKeySet() {
        launchSettings(initialConfig = ServerConfig("https://openwebui.example.com", "sk-key"))
        composeTestRule
            .onNodeWithText(str(com.atomictrxn.conduit.R.string.clear_api_key))
            .assertIsDisplayed()
    }

    @Test
    fun clearApiKeyButtonHiddenWhenKeyEmpty() {
        launchSettings(initialConfig = ServerConfig("https://openwebui.example.com", ""))
        composeTestRule
            .onNodeWithText(str(com.atomictrxn.conduit.R.string.clear_api_key))
            .assertDoesNotExist()
    }

    @Test
    fun syncButtonAlwaysVisible() {
        launchSettings(initialConfig = ServerConfig("https://openwebui.example.com", ""))
        composeTestRule
            .onNodeWithText(str(com.atomictrxn.conduit.R.string.sync_api_key))
            .assertIsDisplayed()
    }

    @Test
    fun syncButtonTriggersCallback() {
        var synced = false
        launchSettings(onSyncApiKey = { synced = true })
        composeTestRule.onNodeWithText(str(com.atomictrxn.conduit.R.string.sync_api_key)).performClick()
        composeTestRule.waitForIdle()
        assertTrue(synced)
    }

    @Test
    fun notificationsSwitchDisabledWithoutApiKey() {
        launchSettings(initialConfig = ServerConfig("https://openwebui.example.com", ""))
        composeTestRule
            .onNodeWithText(str(com.atomictrxn.conduit.R.string.settings_notifications_label))
            .assertIsDisplayed()
        // Switch role node — disabled and off when no API key
        composeTestRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsNotEnabled()
            .assertIsOff()
    }

    @Test
    fun notificationsSwitchEnabledWithApiKey() {
        launchSettings(
            initialConfig = ServerConfig("https://openwebui.example.com", "sk-key"),
            notificationsEnabled = true,
        )
        composeTestRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsEnabled()
    }

    @Test
    fun saveWithBlankUrlShowsErrorAndDoesNotCallOnSave() {
        var saved = false
        val viewModel =
            launchSettings(
                initialConfig = ServerConfig("https://openwebui.example.com", ""),
                onSave = { saved = true },
            )
        viewModel.onServerUrlChanged("")
        composeTestRule.onNodeWithText(str(com.atomictrxn.conduit.R.string.save)).performClick()
        composeTestRule.waitForIdle()
        val errorText = viewModel.uiState.value.urlError
        composeTestRule.onNodeWithText(errorText!!).assertIsDisplayed()
        assertFalse(saved)
    }

    @Test
    fun saveWithInvalidSchemeShowsErrorAndDoesNotCallOnSave() {
        var saved = false
        val viewModel =
            launchSettings(
                initialConfig = ServerConfig("https://openwebui.example.com", ""),
                onSave = { saved = true },
            )
        viewModel.onServerUrlChanged("ftp://openwebui.example.com")
        composeTestRule.onNodeWithText(str(com.atomictrxn.conduit.R.string.save)).performClick()
        composeTestRule.waitForIdle()
        val errorText = viewModel.uiState.value.urlError
        composeTestRule.onNodeWithText(errorText!!).assertIsDisplayed()
        assertFalse(saved)
    }

    @Test
    fun clearApiKeyRemovesStatusLabel() {
        launchSettings(initialConfig = ServerConfig("https://openwebui.example.com", "sk-key"))
        composeTestRule
            .onNodeWithText(str(com.atomictrxn.conduit.R.string.api_key_status_persistent))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(str(com.atomictrxn.conduit.R.string.clear_api_key)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(str(com.atomictrxn.conduit.R.string.api_key_status_persistent))
            .assertDoesNotExist()
    }

    @Test
    fun notificationsHintShownWhenApiKeyAbsent() {
        launchSettings(initialConfig = ServerConfig("https://openwebui.example.com", ""))
        composeTestRule
            .onNodeWithText(str(com.atomictrxn.conduit.R.string.settings_notifications_disabled_hint))
            .assertIsDisplayed()
    }

    @Test
    fun notificationsHintHiddenWhenApiKeyPresent() {
        launchSettings(initialConfig = ServerConfig("https://openwebui.example.com", "sk-key"))
        composeTestRule
            .onNodeWithText(str(com.atomictrxn.conduit.R.string.settings_notifications_disabled_hint))
            .assertDoesNotExist()
    }
}
