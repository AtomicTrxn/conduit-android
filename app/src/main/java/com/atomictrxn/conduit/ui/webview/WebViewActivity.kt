package com.atomictrxn.conduit.ui.webview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.atomictrxn.conduit.ui.settings.SettingsScreen
import com.atomictrxn.conduit.ui.settings.SettingsViewModel
import com.atomictrxn.conduit.ui.theme.ConduitTheme
import com.atomictrxn.conduit.worker.NotificationWorker
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class WebViewActivity : ComponentActivity() {

    private val viewModel: WebViewViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private var webView: WebView? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = result.data?.let { arrayOf(Uri.parse(it.dataString)) } ?: emptyArray()
        filePathCallback?.onReceiveValue(uris.ifEmpty { null })
        filePathCallback = null
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = if (success) cameraImageUri else null
        filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        filePathCallback = null
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val req = pendingPermissionRequest ?: return@registerForActivityResult
        if (granted) {
            req.grant(req.resources)
        } else {
            req.deny()
        }
        pendingPermissionRequest = null
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* user responded; WorkManager already scheduled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        scheduleNotificationWorker()
        requestNotificationPermissionIfNeeded()

        setContent {
            ConduitTheme {
                val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
                val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
                val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
                var toolbarVisible by remember { mutableStateOf(true) }

                LaunchedEffect(serverConfig.serverUrl) {
                    webView?.loadUrl(serverConfig.serverUrl)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    WebViewToolbar(
                        visible = toolbarVisible,
                        onSettingsClick = viewModel::showSettings,
                        onAboutClick = { /* TODO: About dialog */ }
                    )

                    WebViewScreen(
                        serverUrl = serverConfig.serverUrl,
                        connectionState = connectionState,
                        onPageStarted = {
                            viewModel.onPageStarted()
                            toolbarVisible = true
                        },
                        onPageFinished = viewModel::onPageFinished,
                        onError = viewModel::onConnectionError,
                        onWebViewCreated = { wv ->
                            webView = wv
                            wv.webChromeClient = buildChromeClient()
                        }
                    )
                }

                if (showSettings) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onDismiss = {
                            viewModel.dismissSettings()
                            webView?.reload()
                        }
                    )
                }
            }
        }
    }

    private fun buildChromeClient() = object : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams
        ): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = callback

            val cameraFile = File.createTempFile("img_", ".jpg", externalCacheDir)
            cameraImageUri = FileProvider.getUriForFile(
                this@WebViewActivity,
                "${packageName}.fileprovider",
                cameraFile
            )

            val chooser = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*", "application/*"))
            }
            val cameraIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraImageUri)
            }

            val chooserIntent = Intent.createChooser(chooser, getString(com.atomictrxn.conduit.R.string.file_chooser_title)).apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
            }
            try {
                fileChooserLauncher.launch(chooserIntent)
            } catch (e: Exception) {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val audioRequested = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            if (audioRequested) {
                if (ContextCompat.checkSelfPermission(
                        this@WebViewActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    request.grant(request.resources)
                } else {
                    pendingPermissionRequest = request
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                request.grant(request.resources)
            }
        }
    }

    private fun scheduleNotificationWorker() {
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "conduit_notification_poll",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
    }
}
