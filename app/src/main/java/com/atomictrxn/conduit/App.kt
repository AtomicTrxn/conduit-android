package com.atomictrxn.conduit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.webkit.WebView
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        gateSlowWholeDocumentDraw()
        createNotificationChannel()
    }

    private fun gateSlowWholeDocumentDraw() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (am.isTouchExplorationEnabled) {
            WebView.enableSlowWholeDocumentDraw()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_description)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "conduit_chat_completion"
    }
}
