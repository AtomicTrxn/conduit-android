package com.atomictrxn.conduit.ui.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.atomictrxn.conduit.R

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    serverUrl: String,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onError: (String, String?) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
) {
    val currentServerUrl by rememberUpdatedState(serverUrl)

    // The AndroidView must be the ONLY child of its parent — adding any Compose
    // sibling after it causes Compose to create an overlay surface that whites
    // out the WebView on every recomposition (e.g. when the loading indicator
    // appears/disappears).
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    allowContentAccess = true
                    allowFileAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    userAgentString = "Conduit/1.0 (Android) $userAgentString"
                }
                CookieManager.getInstance().let { cm ->
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(this, true)
                }
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView,
                            url: String,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            Log.d("Conduit", "onPageStarted: $url")
                            onPageStarted()
                        }

                        override fun onPageFinished(
                            view: WebView,
                            url: String,
                        ) {
                            Log.d("Conduit", "onPageFinished: $url")
                            CookieManager.getInstance().flush()
                            onPageFinished()
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            Log.e(
                                "Conduit",
                                "onReceivedError: isMainFrame=${request.isForMainFrame} " +
                                    "url=${request.url} err=${error.description}",
                            )
                            if (request.isForMainFrame) {
                                onError(
                                    request.url.toString(),
                                    error.description?.toString(),
                                )
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val url = request.url.toString()
                            val keep = url.startsWith(currentServerUrl)
                            Log.d("Conduit", "shouldOverride: $url | serverUrl=$currentServerUrl | keep=$keep")
                            return if (keep) {
                                false
                            } else {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                true
                            }
                        }
                    }
                onWebViewCreated(this)
                if (serverUrl.isNotBlank()) loadUrl(serverUrl)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
fun ConnectionErrorContent(
    url: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.connection_failed),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.height(48.dp),
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
