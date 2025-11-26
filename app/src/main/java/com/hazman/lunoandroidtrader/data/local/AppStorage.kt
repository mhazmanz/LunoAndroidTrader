package com.hazman.lunoandroidtrader.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * AppStorage is a small wrapper around EncryptedSharedPreferences.
 *
 * It stores:
 * - Luno read-only API key & secret
 * - Telegram bot token & chat ID
 * - Live trading enabled flag
 *
 * All sensitive values are encrypted at rest on the device.
 */
class AppStorage(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun putString(key: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) {
            editor.remove(key)
        } else {
            editor.putString(key, value)
        }
        editor.apply()
    }

    fun setLunoReadOnlyKey(value: String?) = putString(KEY_LUNO_READONLY_KEY, value)
    fun setLunoReadOnlySecret(value: String?) = putString(KEY_LUNO_READONLY_SECRET, value)
    fun setTelegramBotToken(value: String?) = putString(KEY_TELEGRAM_BOT_TOKEN, value)
    fun setTelegramChatId(value: String?) = putString(KEY_TELEGRAM_CHAT_ID, value)

    fun getLunoReadOnlyKey(): String? = prefs.getString(KEY_LUNO_READONLY_KEY, null)
    fun getLunoReadOnlySecret(): String? = prefs.getString(KEY_LUNO_READONLY_SECRET, null)
    fun getTelegramBotToken(): String? = prefs.getString(KEY_TELEGRAM_BOT_TOKEN, null)
    fun getTelegramChatId(): String? = prefs.getString(KEY_TELEGRAM_CHAT_ID, null)

    fun setLiveTradingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_TRADING_ENABLED, enabled).apply()
    }

    fun isLiveTradingEnabled(): Boolean = prefs.getBoolean(KEY_LIVE_TRADING_ENABLED, false)

    companion object {
        private const val PREFS_NAME = "luno_trader_secure_prefs"
        private const val KEY_LUNO_READONLY_KEY = "luno_readonly_key"
        private const val KEY_LUNO_READONLY_SECRET = "luno_readonly_secret"
        private const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
        private const val KEY_LIVE_TRADING_ENABLED = "live_trading_enabled"
    }
}
