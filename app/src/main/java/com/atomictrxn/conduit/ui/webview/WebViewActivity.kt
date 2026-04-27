package com.atomictrxn.conduit.ui.webview

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import com.atomictrxn.conduit.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.atomictrxn.conduit.BuildConfig
import com.atomictrxn.conduit.R
import com.atomictrxn.conduit.ui.about.AboutScreen
import com.atomictrxn.conduit.ui.settings.SettingsScreen
import com.atomictrxn.conduit.ui.settings.SettingsViewModel
import com.atomictrxn.conduit.ui.theme.ConduitTheme
import com.atomictrxn.conduit.worker.NotificationWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class WebViewActivity : ComponentActivity() {
    private val viewModel: WebViewViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private var webView: WebView? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private var cameraImageFile: File? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    // Written from a coroutine, read synchronously in shouldOverrideUrlLoading.
    @Volatile
    private var currentServerUrl: String = ""

    private val fileChooserLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uris = result.data?.let { arrayOf(Uri.parse(it.dataString)) } ?: emptyArray()
            filePathCallback?.onReceiveValue(uris.ifEmpty { null })
            filePathCallback = null
        }

    private val cameraLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
        ) { success ->
            val uri = if (success) cameraImageUri else null
            if (!success) cameraImageFile?.delete()
            filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
            filePathCallback = null
        }

    private val micPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val req = pendingPermissionRequest ?: return@registerForActivityResult
            if (granted) req.grant(req.resources) else req.deny()
            pendingPermissionRequest = null
        }

    private val notifPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { /* user responded; WorkManager already scheduled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView?.canGoBack() == true) {
                        webView?.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )
        scheduleNotificationWorker()
        requestNotificationPermissionIfNeeded()

        val wv = createWebView()
        webView = wv

        // Toolbar ComposeView — WRAP_CONTENT height so it sits above the WebView
        // with no overlap. No transparency tricks needed.
        val toolbarView =
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val connectionState by viewModel.connectionState.collectAsState()
                    ConduitTheme {
                        WebViewToolbar(
                            visible = true,
                            connectionState = connectionState,
                            onSettingsClick = viewModel::showSettings,
                            onAboutClick = viewModel::showAbout,
                        )
                    }
                }
            }

        // Main content: toolbar stacked above WebView, no overlap.
        val mainLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    toolbarView,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
                addView(
                    wv,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).also { it.weight = 1f },
                )
            }

        // Settings full-screen overlay — GONE until needed.
        val settingsView =
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                visibility = View.GONE
                setContent {
                    ConduitTheme {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onDismiss = { viewModel.dismissSettings() },
                            onSave = {
                                viewModel.dismissSettings()
                                wv.reload()
                            },
                            onSyncApiKey = { injectTokenBridge(wv) },
                        )
                    }
                }
            }

        // About full-screen overlay — GONE until needed.
        val aboutView =
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                visibility = View.GONE
                setContent {
                    ConduitTheme {
                        AboutScreen(onDismiss = viewModel::dismissAbout)
                    }
                }
            }

        // Root: FrameLayout so overlays can cover everything when shown.
        val root =
            FrameLayout(this).apply {
                addView(mainLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(settingsView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(aboutView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        setContentView(root)

        // Show/hide the settings overlay in response to ViewModel state.
        lifecycleScope.launch {
            viewModel.showSettings.collect { show ->
                if (show) settingsViewModel.loadCurrentConfig()
                settingsView.visibility = if (show) View.VISIBLE else View.GONE
            }
        }

        // Show/hide the about overlay in response to ViewModel state.
        lifecycleScope.launch {
            viewModel.showAbout.collect { show ->
                aboutView.visibility = if (show) View.VISIBLE else View.GONE
            }
        }

        // Load the server URL whenever it changes, then handle any pending chat deep-link.
        lifecycleScope.launch {
            viewModel.serverConfig.collect { config ->
                val newUrl = config.serverUrl
                currentServerUrl = newUrl
                if (newUrl.isNotBlank()) {
                    val loaded = wv.url ?: ""
                    if (!loaded.startsWith(newUrl)) {
                        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
                        if (chatId != null) {
                            wv.loadUrl("$newUrl/c/${Uri.encode(chatId.take(128))}")
                        } else {
                            wv.loadUrl(newUrl)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    // Called when the activity is already running and a new notification tap arrives.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        if (currentServerUrl.isNotBlank()) {
            webView?.loadUrl("$currentServerUrl/c/${Uri.encode(chatId.take(128))}")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val wv = WebView(this)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowContentAccess = false
            allowFileAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = "Conduit/1.0 (Android) ${wv.settings.userAgentString}"
        }
        CookieManager.getInstance().let { cm ->
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(wv, false)
        }
        wv.webViewClient =
            object : WebViewClient() {
                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: android.graphics.Bitmap?,
                ) {
                    Log.d("Conduit", "onPageStarted: $url")
                    viewModel.onPageStarted()
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String,
                ) {
                    Log.d("Conduit", "onPageFinished: $url")
                    CookieManager.getInstance().flush()
                    viewModel.onPageFinished()
                    val storedKey = viewModel.serverConfig.value.apiKey
                    if (currentServerUrl.isNotBlank() &&
                        url.startsWith(currentServerUrl) &&
                        jwtNeedsRefresh(storedKey)
                    ) {
                        injectTokenBridge(view)
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    Log.e("Conduit", "onReceivedError: isMainFrame=${request.isForMainFrame} url=${request.url} err=${error.description}")
                    if (request.isForMainFrame) {
                        viewModel.onConnectionError(request.url.toString(), error.description?.toString())
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val url = request.url.toString()
                    val scheme = request.url.scheme?.lowercase()
                    if (currentServerUrl.isNotBlank() && url.startsWith(currentServerUrl)) {
                        Log.d("Conduit", "shouldOverride: keeping in WebView: $url")
                        return false
                    }
                    if (scheme == "http" || scheme == "https") {
                        Log.d("Conduit", "shouldOverride: opening externally: $url")
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } else {
                        Log.w("Conduit", "shouldOverride: blocked non-http(s) URL scheme '$scheme': $url")
                    }
                    return true
                }
            }
        wv.addJavascriptInterface(TokenBridge(), "ConduitBridge")
        wv.webChromeClient = buildChromeClient()
        return wv
    }

    private fun buildChromeClient() =
        object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback

                cameraImageFile?.delete()
                val cameraFile = File.createTempFile("img_", ".jpg", externalCacheDir)
                cameraImageFile = cameraFile
                cameraImageUri =
                    FileProvider.getUriForFile(
                        this@WebViewActivity,
                        "$packageName.fileprovider",
                        cameraFile,
                    )

                val chooser =
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*", "application/*"))
                    }
                val cameraIntent =
                    Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraImageUri)
                    }
                val chooserIntent =
                    Intent.createChooser(chooser, getString(R.string.file_chooser_title)).apply {
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

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d("Conduit.JS", "${message.messageLevel()} ${message.message()} [${message.sourceId()}:${message.lineNumber()}]")
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val audioRequested = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                if (audioRequested) {
                    if (ContextCompat.checkSelfPermission(
                            this@WebViewActivity,
                            Manifest.permission.RECORD_AUDIO,
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
        val wm = WorkManager.getInstance(this)
        val periodicRequest =
            PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .build()
        wm.enqueueUniquePeriodicWork(
            "conduit_notification_poll",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )
        if (BuildConfig.DEBUG) {
            wm.enqueue(androidx.work.OneTimeWorkRequestBuilder<NotificationWorker>().build())
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun jwtNeedsRefresh(storedKey: String): Boolean {
        if (storedKey.isBlank()) return true
        val parts = storedKey.split(".")
        if (parts.size != 3) return false  // not a JWT — permanent API key, never refresh
        return try {
            val payload = android.util.Base64.decode(
                parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
            )
            val json = org.json.JSONObject(String(payload))
            val exp = json.optLong("exp", 0L)
            val refreshThreshold = System.currentTimeMillis() / 1000L + 24 * 60 * 60
            exp < refreshThreshold
        } catch (e: Exception) {
            true  // can't decode — refresh to be safe
        }
    }

    fun injectTokenBridge(wv: WebView) {
        wv.evaluateJavascript(
            "(function(){ var t = localStorage.getItem('token'); if(t) ConduitBridge.onToken(t); })()",
            null,
        )
    }

    @Keep
    inner class TokenBridge {
        @JavascriptInterface
        fun onToken(jwt: String) {
            if (jwt.isBlank()) return
            val serverUrl = currentServerUrl
            if (serverUrl.isBlank()) return
            lifecycleScope.launch(Dispatchers.IO) {
                val permanentKey = runCatching {
                    ApiClient.create(serverUrl, jwt).getApiKey().apiKey.takeIf { it.isNotBlank() }
                }.getOrNull()

                if (permanentKey != null) {
                    viewModel.saveApiKey(permanentKey)
                    Log.d("Conduit", "API key synced from session")
                } else {
                    // API key endpoint unavailable — store JWT directly.
                    // It works as a Bearer token and auto-refreshes on each page load.
                    viewModel.saveApiKey(jwt)
                    Log.d("Conduit", "Session token saved (API key endpoint unavailable)")
                }
                settingsViewModel.loadCurrentConfig()
            }
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
    }
}
