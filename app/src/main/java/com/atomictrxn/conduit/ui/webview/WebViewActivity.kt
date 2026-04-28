package com.atomictrxn.conduit.ui.webview

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.Keep
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
import com.atomictrxn.conduit.data.api.ApiClient
import com.atomictrxn.conduit.domain.auth.JwtRefreshPolicy
import com.atomictrxn.conduit.domain.model.ConnectionState
import com.atomictrxn.conduit.domain.navigation.ExternalLinkAction
import com.atomictrxn.conduit.domain.navigation.ExternalLinkPolicy
import com.atomictrxn.conduit.domain.navigation.WebViewNavigation
import com.atomictrxn.conduit.ui.about.AboutScreen
import com.atomictrxn.conduit.ui.settings.SettingsScreen
import com.atomictrxn.conduit.ui.settings.SettingsViewModel
import com.atomictrxn.conduit.ui.splash.ConduitSplash
import com.atomictrxn.conduit.ui.theme.ConduitTheme
import com.atomictrxn.conduit.worker.NotificationWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
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
    private var currentChatUrl: String? = null
    private var previousChatUrl: String? = null
    private var notificationChatUrl: String? = null
    private var hasLoadedInitialUrl = false
    private var startupSplashView: View? = null
    private var startupSplashDismissed = false

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
                    when {
                        viewModel.showAbout.value -> viewModel.dismissAbout()
                        viewModel.showSettings.value -> viewModel.dismissSettings()
                        notificationChatUrl != null && previousChatUrl != null -> {
                            webView?.loadUrl(previousChatUrl!!)
                            notificationChatUrl = null
                            previousChatUrl = null
                        }
                        webView?.canGoBack() == true -> webView?.goBack()
                        else -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            },
        )
        scheduleNotificationWorker()
        requestNotificationPermissionIfNeeded()

        val wv = createWebView()
        webView = wv
        val initialNotificationChatId = intent.getStringExtra(EXTRA_CHAT_ID)
        hasLoadedInitialUrl = initialNotificationChatId == null && restoreWebViewState(wv, savedInstanceState)
        val showStartupSplash =
            savedInstanceState == null &&
                intent.getBooleanExtra(EXTRA_SHOW_STARTUP_SPLASH, false)

        // Toolbar ComposeView — WRAP_CONTENT height so it sits above the WebView
        // with no overlap. No transparency tricks needed.
        val toolbarView =
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val connectionState by viewModel.connectionState.collectAsState()
                    val pageTitle by viewModel.pageTitle.collectAsState()
                    ConduitTheme {
                        WebViewToolbar(
                            visible = true,
                            connectionState = connectionState,
                            title = pageTitle,
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
                                val urlChanged = settingsViewModel.uiState.value.urlChanged
                                viewModel.dismissSettings()
                                if (urlChanged) wv.loadUrl(currentServerUrl)
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

        val startupSplashOverlay =
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                visibility = if (showStartupSplash) View.VISIBLE else View.GONE
                alpha = if (showStartupSplash) 1f else 0f
                setContent {
                    ConduitTheme {
                        ConduitSplash()
                    }
                }
            }
        startupSplashView = startupSplashOverlay.takeIf { showStartupSplash }

        // Root: FrameLayout so overlays can cover everything when shown.
        val root =
            FrameLayout(this).apply {
                addView(mainLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(settingsView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(aboutView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(
                    startupSplashOverlay,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
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

        lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    dismissStartupSplash()
                }
            }
        }

        // Load the initial target once: notification chat, newest API chat, persisted chat, then root.
        lifecycleScope.launch {
            viewModel.serverConfig.collect { config ->
                val newUrl = config.serverUrl
                currentServerUrl = newUrl
                if (newUrl.isNotBlank() && !hasLoadedInitialUrl) {
                    hasLoadedInitialUrl = true
                    if (initialNotificationChatId != null) {
                        loadNotificationChat(wv, newUrl, initialNotificationChatId)
                    } else {
                        wv.loadUrl(viewModel.initialUrlFor(config))
                    }
                }
            }
        }

        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val webViewState = Bundle()
        webView?.saveState(webViewState)
        outState.putBundle(KEY_WEBVIEW_STATE, webViewState)
    }

    // Called when the activity is already running and a new notification tap arrives.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        if (currentServerUrl.isNotBlank()) {
            webView?.let { loadNotificationChat(it, currentServerUrl, chatId) }
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
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
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
                    WebViewNavigation.chatLocationFor(currentServerUrl, url)?.let { chat ->
                        currentChatUrl = chat.url
                        viewModel.saveLastChat(chat.id, chat.url)
                    }
                    val storedKey = viewModel.serverConfig.value.apiKey
                    if (currentServerUrl.isNotBlank() &&
                        url.startsWith(currentServerUrl) &&
                        JwtRefreshPolicy.needsRefresh(storedKey)
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
                    when (val action = ExternalLinkPolicy.decide(currentServerUrl, url)) {
                        ExternalLinkAction.KeepInWebView -> {
                            Log.d("Conduit", "shouldOverride: keeping in WebView: $url")
                            return false
                        }
                        ExternalLinkAction.Download -> {
                            Log.d("Conduit", "shouldOverride: downloading external file: $url")
                            enqueueDownload(url, request.requestHeaders["User-Agent"], null, null)
                        }
                        ExternalLinkAction.OpenExternally -> {
                            Log.d("Conduit", "shouldOverride: opening externally: $url")
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                        is ExternalLinkAction.Block -> {
                            Log.w("Conduit", "shouldOverride: blocked non-http(s) URL scheme '${action.scheme}': $url")
                        }
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

            override fun onReceivedTitle(
                view: WebView,
                title: String?,
            ) {
                viewModel.onPageTitleChanged(title)
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

    private fun restoreWebViewState(
        webView: WebView,
        savedInstanceState: Bundle?,
    ): Boolean {
        val webViewState = savedInstanceState?.getBundle(KEY_WEBVIEW_STATE) ?: return false
        return webView.restoreState(webViewState) != null
    }

    private fun loadNotificationChat(
        webView: WebView,
        serverUrl: String,
        chatId: String,
    ) {
        val targetUrl = WebViewNavigation.chatUrl(serverUrl, chatId)
        previousChatUrl = currentChatUrl?.takeIf { it != targetUrl }
        notificationChatUrl = targetUrl
        webView.loadUrl(targetUrl)
    }

    private fun dismissStartupSplash() {
        val splash = startupSplashView ?: return
        if (startupSplashDismissed) return
        startupSplashDismissed = true
        splash.animate()
            .alpha(0f)
            .setDuration(STARTUP_SPLASH_FADE_MS)
            .withEndAction {
                splash.visibility = View.GONE
                startupSplashView = null
            }
            .start()
    }

    private fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        try {
            val uri = Uri.parse(url)
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request =
                DownloadManager.Request(uri)
                    .setTitle(fileName)
                    .setDescription(uri.host ?: getString(R.string.app_name))
                    .setMimeType(mimeType)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

            userAgent?.takeIf { it.isNotBlank() }?.let {
                request.addRequestHeader("User-Agent", it)
            }
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                request.addRequestHeader("Cookie", it)
            }

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("Conduit", "Failed to start download: $url", e)
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        Toast.makeText(this, R.string.share_received, Toast.LENGTH_SHORT).show()
        if (currentServerUrl.isNotBlank()) {
            webView?.loadUrl(currentServerUrl)
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
                val permanentKey =
                    runCatching {
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
        const val EXTRA_SHOW_STARTUP_SPLASH = "extra_show_startup_splash"
        private const val KEY_WEBVIEW_STATE = "webview_state"
        private const val STARTUP_SPLASH_FADE_MS = 280L
    }
}
