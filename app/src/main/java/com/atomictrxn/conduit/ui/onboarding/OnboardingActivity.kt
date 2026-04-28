package com.atomictrxn.conduit.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.atomictrxn.conduit.data.repository.ServerRepository
import com.atomictrxn.conduit.ui.theme.ConduitTheme
import com.atomictrxn.conduit.ui.webview.WebViewActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {
    @Inject lateinit var repository: ServerRepository
    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ConduitTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    if (repository.onboardingComplete.first()) launchWebView()
                }

                LaunchedEffect(uiState.isComplete) {
                    if (uiState.isComplete) launchWebView()
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "welcome",
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable("welcome") {
                            WelcomeScreen(
                                onGetStarted = { navController.navigate("server_setup") },
                            )
                        }
                        composable("server_setup") {
                            ServerSetupScreen(
                                serverUrl = uiState.serverUrl,
                                urlError = uiState.urlError,
                                onServerUrlChanged = viewModel::onServerUrlChanged,
                                onNext = {
                                    if (viewModel.submitServerUrl()) {
                                        navController.navigate("api_key")
                                    }
                                },
                            )
                        }
                        composable("api_key") {
                            ApiKeyScreen(
                                apiKey = uiState.apiKey,
                                onApiKeyChanged = viewModel::onApiKeyChanged,
                                onContinue = viewModel::completeOnboarding,
                                onSkip = viewModel::skipApiKey,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun launchWebView() {
        startActivity(
            Intent(this, WebViewActivity::class.java)
                .putExtra(WebViewActivity.EXTRA_SHOW_STARTUP_SPLASH, true),
        )
        finish()
    }
}
