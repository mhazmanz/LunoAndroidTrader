package com.hazman.lunoandroidtrader.domain.notifications

/**
 * Abstraction for sending notifications about strategy events, trades, or errors.
 *
 * Implementations can:
 *  - send Telegram messages
 *  - show local Android notifications
 *  - or both
 *
 * The ViewModel / domain code only depends on this interface and does not care
 * about HOW the notification is delivered.
 */
interface NotificationDispatcher {

    /**
     * Send a notification carrying a human-readable message about something
     * that happened in the trading engine (e.g. a new trade opened).
     *
     * Implementations should:
     *  - Prefer Telegram if properly configured and working
     *  - Fallback to a local Android notification if Telegram is not configured
     *    or fails
     */
    suspend fun notifySignal(message: String)
}
