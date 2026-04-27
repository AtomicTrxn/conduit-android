package com.atomictrxn.conduit.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atomictrxn.conduit.App
import com.atomictrxn.conduit.R
import com.atomictrxn.conduit.data.api.ApiClient
import com.atomictrxn.conduit.data.repository.ServerRepository
import com.atomictrxn.conduit.ui.webview.WebViewActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class NotificationWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted workerParams: WorkerParameters,
        private val repository: ServerRepository,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val config = repository.serverConfig.first()
            if (!config.hasApiKey) return Result.success()

            val notificationsEnabled = repository.notificationsEnabled.first()
            if (!notificationsEnabled) return Result.success()

            return try {
                val now = System.currentTimeMillis() / 1000L
                val lastChecked = repository.lastNotificationCheck.first()

                // First run: record current time and skip — don't notify for pre-existing chats.
                if (lastChecked == 0L) {
                    repository.setLastNotificationCheck(now)
                    return Result.success()
                }

                val service = ApiClient.create(config.serverUrl, config.apiKey)
                val chats = service.getChats()
                val newChats = chats.filter { it.updatedAt > lastChecked }.take(10)

                newChats.forEach { chat ->
                    showNotification(chat.id, chat.title)
                }

                repository.setLastNotificationCheck(now)
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }

        private fun showNotification(
            chatId: String,
            title: String,
        ) {
            val intent =
                Intent(context, WebViewActivity::class.java).apply {
                    putExtra(WebViewActivity.EXTRA_CHAT_ID, chatId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    chatId.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notification =
                NotificationCompat.Builder(context, App.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.notification_title))
                    .setContentText(title)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .build()

            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context)
                    .notify(chatId.hashCode(), notification)
            }
        }
    }
