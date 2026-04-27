package com.atomictrxn.conduit.ui.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.atomictrxn.conduit.R
import com.atomictrxn.conduit.domain.model.ConnectionState

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    serverUrl: String,
    connectionState: ConnectionState,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onError: (String, String?) -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (connectionState is ConnectionState.Error) {
            ConnectionErrorContent(
                url = connectionState.url,
                onRetry = { /* handled via WebView reload in Activity */ }
            )
        }

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        userAgentString = "Conduit/1.0 (Android) $userAgentString"
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView,
                            url: String,
                            favicon: android.graphics.Bitmap?
                        ) {
                            onPageStarted()
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            onPageFinished()
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                onError(
                                    request.url.toString(),
                                    error.description?.toString()
                                )
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val uri = request.url
                            return if (uri.host == Uri.parse(serverUrl).host) {
                                false
                            } else {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, uri)
                                )
                                true
                            }
                        }
                    }
                    onWebViewCreated(this)
                    if (serverUrl.isNotBlank()) loadUrl(serverUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (connectionState is ConnectionState.Loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConnectionErrorContent(url: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.connection_failed),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.height(48.dp)
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
