package com.atomictrxn.conduit.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.atomictrxn.conduit.data.repository.ServerRepository
import com.atomictrxn.conduit.ui.onboarding.OnboardingActivity
import com.atomictrxn.conduit.ui.theme.ConduitTheme
import com.atomictrxn.conduit.ui.webview.WebViewActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : ComponentActivity() {
    @Inject lateinit var repository: ServerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ConduitTheme {
                ConduitSplash()

                LaunchedEffect(Unit) {
                    val onboardingComplete = repository.onboardingComplete.first()
                    val config = repository.serverConfig.first()
                    val target =
                        if (onboardingComplete && config.isConfigured) {
                            WebViewActivity::class.java
                        } else {
                            OnboardingActivity::class.java
                        }
                    val intent =
                        Intent(this@SplashActivity, target).apply {
                            if (target == WebViewActivity::class.java) {
                                putExtra(WebViewActivity.EXTRA_SHOW_STARTUP_SPLASH, true)
                            }
                        }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                    overridePendingTransition(0, 0)
                }
            }
        }
    }
}
