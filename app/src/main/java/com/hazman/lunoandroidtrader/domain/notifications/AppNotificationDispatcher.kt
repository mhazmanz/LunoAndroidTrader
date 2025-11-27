package com.hazman.lunoandroidtrader.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hazman.lunoandroidtrader.MainActivity
import com.hazman.lunoandroidtrader.R
import com.hazman.lunoandroidtrader.data.local.AppStorage
import com.hazman.lunoandroidtrader.data.telegram.TelegramClient
import com.hazman.lunoandroidtrader.domain.notifications.NotificationDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concrete implementation of [NotificationDispatcher] for the Android app.
 *
 * Responsibilities:
 *  - Try to send trading signals to Telegram first, using [TelegramClient].
 *  - If Telegram is not configured or fails for any reason, always fall back
 *    to a local Android notification.
 *  - Ensure a high-importance notification channel exists for the app.
 *
 * Design:
 *  - All network calls are done on Dispatchers.IO.
 *  - This class does *not* catch errors silently: any Telegram failure
 *    leads to a local notification with a short explanation.
 */
class AppNotificationDispatcher(
    private val context: Context,
    private val storage: AppStorage,
    private val telegramClient: TelegramClient
) : NotificationDispatcher {

    companion object {
        private const val CHANNEL_ID = "trading_signals_channel"
        private const val CHANNEL_NAME = "Trading Signals"
        private const val CHANNEL_DESCRIPTION = "Notifications for paper and live trading signals."

        // Base ID; we increment so multiple signals don't overwrite each other.
        private val notificationIdGenerator = AtomicInteger(10_000)
    }

    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    init {
        ensureNotificationChannel()
    }

    /**
     * Public API used by ViewModels/domain to notify about trading signals.
     *
     * Behavior:
     *  1. Try Telegram first.
     *  2. If Telegram succeeds -> done.
     *  3. If Telegram fails for *any* reason (including missing config),
     *     show a local Android notification with the message and a brief
     *     fallback reason.
     */
    override suspend fun notifySignal(message: String) {
        // Try Telegram on IO dispatcher.
        val telegramResult = withContext(Dispatchers.IO) {
            try {
                telegramClient.sendMessage(message)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        if (telegramResult.isSuccess) {
            // Telegram delivery OK – nothing else to do.
            return
        }

        val error = telegramResult.exceptionOrNull()
        val fallbackReason = error?.message
            ?: "Telegram is not configured or failed."

        // Always fall back to local notification.
        showLocalNotification(
            message = message,
            fallbackReason = fallbackReason
        )
    }

    /**
     * Ensure the notification channel exists (Android 8+).
     * Safe to call multiple times; creation is idempotent.
     */
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

        val existingChannel = manager.getNotificationChannel(CHANNEL_ID)
        if (existingChannel != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableLights(true)
            enableVibration(true)
        }

        manager.createNotificationChannel(channel)
    }

    /**
     * Show a local Android notification for the given signal message.
     *
     * @param message        The main trading signal text.
     * @param fallbackReason Short explanation why Telegram was not used.
     */
    private fun showLocalNotification(
        message: String,
        fallbackReason: String?
    ) {
        // Android 13+ requires POST_NOTIFICATIONS permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                // Cannot show notifications; nothing more we can do here.
                // The app should request this permission from UI elsewhere.
                return
            }
        }

        val title = "Trading Signal"

        val fullText = buildString {
            append(message.trim())
            if (!fallbackReason.isNullOrBlank()) {
                append("\n\n(Fell back to local notification: ")
                append(fallbackReason.trim())
                append(")")
            }
        }

        // Tap action: open the app's main activity.
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            pendingFlags
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // Use app launcher icon
            .setContentTitle(title)
            .setContentText(fullText.take(120)) // Single-line summary
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationId = notificationIdGenerator.getAndIncrement()
        notificationManager.notify(notificationId, builder.build())
    }
}
