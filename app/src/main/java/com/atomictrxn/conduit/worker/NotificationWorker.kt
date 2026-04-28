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
import com.atomictrxn.conduit.ui.webview.WebViewActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class NotificationWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted workerParams: WorkerParameters,
        private val poller: NotificationPoller,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            return when (val result = poller.poll()) {
                is NotificationPollResult.Success -> {
                    result.chats.forEach { chat ->
                        showNotification(chat.id, chat.title)
                    }
                    Result.success()
                }
                NotificationPollResult.Retry -> Result.retry()
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
            val notificationId = (chatId.toLongOrNull() ?: chatId.fold(0L) { acc, c -> acc * 31 + c.code }).toInt()
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    notificationId,
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
                    .notify(notificationId, notification)
            }
        }
    }
