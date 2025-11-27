package com.hazman.lunoandroidtrader.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hazman.lunoandroidtrader.R
import com.hazman.lunoandroidtrader.data.local.AppStorage
import com.hazman.lunoandroidtrader.data.telegram.TelegramClient
import com.hazman.lunoandroidtrader.domain.notifications.NotificationDispatcher

/**
 * Application-level implementation of [NotificationDispatcher].
 *
 * Behavior:
 *  - If Telegram bot token + chat ID exist in AppStorage:
 *      - Try sending the message via Telegram.
 *      - If sending fails, fall back to a local Android notification.
 *  - If Telegram config is missing:
 *      - Show a local Android notification directly.
 */
class AppNotificationDispatcher(
    private val context: Context,
    private val storage: AppStorage,
    private val telegramClient: TelegramClient
) : NotificationDispatcher {

    companion object {
        private const val CHANNEL_ID_SIGNALS = "trader_signals_channel"
        private const val CHANNEL_NAME_SIGNALS = "Trading Signals"
        private const val CHANNEL_DESC_SIGNALS =
            "Notifications for simulated and live trading events from Luno Android Trader."
    }

    override suspend fun notifySignal(message: String) {
        val botToken = storage.getTelegramBotToken().orEmpty().trim()
        val chatId = storage.getTelegramChatId().orEmpty().trim()

        val hasTelegramConfig = botToken.isNotEmpty() && chatId.isNotEmpty()

        if (hasTelegramConfig) {
            val result = telegramClient.sendMessage(message)
            if (result.isSuccess) {
                // Successfully sent via Telegram – nothing else to do.
                return
            }
            // If Telegram failed (network, invalid credentials, etc.),
            // we fall back to a local notification.
        }

        showLocalNotification(message)
    }

    private fun showLocalNotification(message: String) {
        createChannelIfNeeded()

        val notificationId = (System.currentTimeMillis() and 0xFFFFFFF).toInt()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SIGNALS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Luno Android Trader")
            .setContentText(
                if (message.length > 60) {
                    message.take(57) + "..."
                } else {
                    message
                }
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val existing = manager.getNotificationChannel(CHANNEL_ID_SIGNALS)
        if (existing != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID_SIGNALS,
            CHANNEL_NAME_SIGNALS,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC_SIGNALS
        }

        manager.createNotificationChannel(channel)
    }
}
