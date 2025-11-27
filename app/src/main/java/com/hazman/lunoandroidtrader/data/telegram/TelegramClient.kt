package com.hazman.lunoandroidtrader.data.telegram

import com.hazman.lunoandroidtrader.data.local.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin, synchronous-by-design wrapper around the Telegram Bot API.
 *
 * Responsibilities:
 *  - Read bot token + chat id from [AppStorage]
 *  - Send simple text messages to that chat using Telegram Bot API
 *  - Wrap all outcomes in Kotlin [Result] for easy success/failure handling
 *
 * This class does not know anything about Android UI. It is safe to call from
 * any coroutine; network I/O is always done on [Dispatchers.IO].
 */
class TelegramClient(
    private val storage: AppStorage
) {

    /**
     * Send a plain-text message via Telegram's Bot API using the bot token and
     * chat ID stored in [AppStorage].
     *
     * @param text Human-readable message body.
     *
     * @return
     *  - Result.success(Unit) if HTTP 200-299 and `"ok":true` in the response body
     *  - Result.failure(...) otherwise (missing config, HTTP error, or exception)
     */
    suspend fun sendMessage(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = storage.getTelegramBotToken()?.trim().orEmpty()
        val chatId = storage.getTelegramChatId()?.trim().orEmpty()

        if (token.isEmpty() || chatId.isEmpty()) {
            // No configuration – signal failure so caller can fall back to local notification
            return@withContext Result.failure(
                IllegalStateException("Telegram token or chat ID is not configured.")
            )
        }

        try {
            // URL-encode the text to be safe.
            val encodedText = URLEncoder.encode(text, "UTF-8")

            // Basic Bot API sendMessage endpoint.
            val urlString =
                "https://api.telegram.org/bot$token/sendMessage" +
                        "?chat_id=$chatId&text=$encodedText&parse_mode=Markdown"

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                doInput = true
            }

            val statusCode = connection.responseCode

            val responseBody = try {
                val stream = if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                stream?.use { s ->
                    BufferedReader(InputStreamReader(s)).use { reader ->
                        reader.readText()
                    }
                } ?: ""
            } catch (e: Exception) {
                ""
            } finally {
                connection.disconnect()
            }

            if (statusCode !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("Telegram HTTP $statusCode: $responseBody")
                )
            }

            // Very small "ok":true check to confirm Telegram accepted the message.
            // This avoids pulling a full JSON library here.
            val ok = responseBody.contains("\"ok\":true")
            if (!ok) {
                return@withContext Result.failure(
                    IllegalStateException("Telegram response not OK: $responseBody")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convenience helper used by SettingsScreen's "Send test message" button.
     *
     * - Uses the existing stored token + chat ID.
     * - Sends a fixed diagnostic message.
     * - Calls [onSuccess] on success, [onError] with a human-readable message on failure.
     *
     * This is suspend because it performs network I/O; call it from a coroutine
     * (e.g. inside rememberCoroutineScope().launch { ... }).
     */
    suspend fun sendTestMessage(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val result = sendMessage("Test message from Luno Android Trader ✅")

        result.fold(
            onSuccess = {
                onSuccess()
            },
            onFailure = { e ->
                val msg = e.message ?: e::class.java.simpleName ?: "Unknown error"
                onError("Failed to send Telegram test message: $msg")
            }
        )
    }
}
