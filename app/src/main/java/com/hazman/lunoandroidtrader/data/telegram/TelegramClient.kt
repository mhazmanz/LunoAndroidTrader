package com.hazman.lunoandroidtrader.data.telegram

import com.hazman.lunoandroidtrader.data.local.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject

/**
 * Simple Telegram Bot API client for sending messages.
 *
 * For now we only implement a "send test message" call.
 */
class TelegramClient(
    private val storage: AppStorage
) {

    private val client: OkHttpClient

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    /**
     * Sends a test message to the configured Telegram chat.
     *
     * Returns:
     * - Result.success(Unit) on success
     * - Result.failure(exception) on error
     */
    suspend fun sendTestMessage(
        text: String = "LunoAndroidTrader: Telegram connectivity test"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = storage.getTelegramBotToken()
            val chatId = storage.getTelegramChatId()

            if (token.isNullOrEmpty() || chatId.isNullOrEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Telegram bot token or chat ID is not set.")
                )
            }

            // Telegram Bot API URL for sendMessage
            val url = "https://api.telegram.org/bot$token/sendMessage"

            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Telegram API error: HTTP ${response.code} ${response.message}")
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
