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
 * - Risk configuration:
 *   - riskPerTradePercent
 *   - dailyLossLimitPercent
 *   - maxTradesPerDay
 *   - cooldownMinutesAfterLoss
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

    // ------------------
    // LUNO / TELEGRAM
    // ------------------

    fun setLunoReadOnlyKey(value: String?) = putString(KEY_LUNO_READONLY_KEY, value)
    fun setLunoReadOnlySecret(value: String?) = putString(KEY_LUNO_READONLY_SECRET, value)
    fun setTelegramBotToken(value: String?) = putString(KEY_TELEGRAM_BOT_TOKEN, value)
    fun setTelegramChatId(value: String?) = putString(KEY_TELEGRAM_CHAT_ID, value)

    fun getLunoReadOnlyKey(): String? = prefs.getString(KEY_LUNO_READONLY_KEY, null)
    fun getLunoReadOnlySecret(): String? = prefs.getString(KEY_LUNO_READONLY_SECRET, null)
    fun getTelegramBotToken(): String? = prefs.getString(KEY_TELEGRAM_BOT_TOKEN, null)
    fun getTelegramChatId(): String? = prefs.getString(KEY_TELEGRAM_CHAT_ID, null)

    // ------------------
    // LIVE TRADING FLAG
    // ------------------

    fun setLiveTradingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_TRADING_ENABLED, enabled).apply()
    }

    fun isLiveTradingEnabled(): Boolean = prefs.getBoolean(KEY_LIVE_TRADING_ENABLED, false)

    // ------------------
    // RISK CONFIGURATION
    // ------------------

    /**
     * Risk per trade as a percentage of account equity.
     * Example: 1.0 means 1% risk per trade.
     */
    fun setRiskPerTradePercent(value: Double) {
        prefs.edit().putString(KEY_RISK_PER_TRADE_PERCENT, value.toString()).apply()
    }

    fun getRiskPerTradePercent(): Double {
        val raw = prefs.getString(KEY_RISK_PER_TRADE_PERCENT, null)
        return raw?.toDoubleOrNull() ?: DEFAULT_RISK_PER_TRADE_PERCENT
    }

    /**
     * Daily loss limit as a percentage of starting-day equity.
     * Example: 5.0 means stop trading after 5% loss in a day.
     */
    fun setDailyLossLimitPercent(value: Double) {
        prefs.edit().putString(KEY_DAILY_LOSS_LIMIT_PERCENT, value.toString()).apply()
    }

    fun getDailyLossLimitPercent(): Double {
        val raw = prefs.getString(KEY_DAILY_LOSS_LIMIT_PERCENT, null)
        return raw?.toDoubleOrNull() ?: DEFAULT_DAILY_LOSS_LIMIT_PERCENT
    }

    /**
     * Maximum number of trades allowed per day.
     */
    fun setMaxTradesPerDay(value: Int) {
        prefs.edit().putInt(KEY_MAX_TRADES_PER_DAY, value).apply()
    }

    fun getMaxTradesPerDay(): Int {
        return prefs.getInt(KEY_MAX_TRADES_PER_DAY, DEFAULT_MAX_TRADES_PER_DAY)
    }

    /**
     * Cooldown in minutes after a loss or hitting daily loss limit.
     */
    fun setCooldownMinutesAfterLoss(value: Int) {
        prefs.edit().putInt(KEY_COOLDOWN_MINUTES_AFTER_LOSS, value).apply()
    }

    fun getCooldownMinutesAfterLoss(): Int {
        return prefs.getInt(KEY_COOLDOWN_MINUTES_AFTER_LOSS, DEFAULT_COOLDOWN_MINUTES_AFTER_LOSS)
    }

    companion object {
        private const val PREFS_NAME = "luno_trader_secure_prefs"

        // Keys for Luno and Telegram
        private const val KEY_LUNO_READONLY_KEY = "luno_readonly_key"
        private const val KEY_LUNO_READONLY_SECRET = "luno_readonly_secret"
        private const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
        private const val KEY_LIVE_TRADING_ENABLED = "live_trading_enabled"

        // Keys for risk configuration
        private const val KEY_RISK_PER_TRADE_PERCENT = "risk_per_trade_percent"
        private const val KEY_DAILY_LOSS_LIMIT_PERCENT = "daily_loss_limit_percent"
        private const val KEY_MAX_TRADES_PER_DAY = "max_trades_per_day"
        private const val KEY_COOLDOWN_MINUTES_AFTER_LOSS = "cooldown_minutes_after_loss"

        // Default values
        private const val DEFAULT_RISK_PER_TRADE_PERCENT = 1.0
        private const val DEFAULT_DAILY_LOSS_LIMIT_PERCENT = 5.0
        private const val DEFAULT_MAX_TRADES_PER_DAY = 10
        private const val DEFAULT_COOLDOWN_MINUTES_AFTER_LOSS = 30
    }
}
